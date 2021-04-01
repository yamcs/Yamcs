import { BehaviorSubject, Subscription } from 'rxjs';
import { Alarm, NamedObjectId, ParameterSubscription, ParameterValue, Sample, Range } from '../../client';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { convertValueToNumber, isEnumerationType } from '../utils';
import { CustomBarsValue, DyAnnotation, DySample } from './dygraphs';
import { NamedParameterType } from './NamedParameterType';
import { DyValueRange, PlotBuffer, PlotData } from './PlotBuffer';

/**
 * Stores sample data for use in a ParameterPlot directly
 * in DyGraphs native format.
 *
 * See http://dygraphs.com/data.html#array
 */
export class DyDataSource {

  public loading$ = new BehaviorSubject<boolean>(false);

  data$ = new BehaviorSubject<PlotData>({
    valueRange: [null, null],
    samples: [],
    annotations: [],
  });
  minValue?: number;
  maxValue?: number;

  visibleStart: Date;
  visibleStop: Date;

  parameters$ = new BehaviorSubject<NamedParameterType[]>([]);
  private plotBuffer: PlotBuffer;

  private lastLoadPromise: Promise<any> | null;

  // Realtime
  private realtimeSubscription: ParameterSubscription;
  private syncSubscription: Subscription;
  // Added due to multi-param plots where realtime values are not guaranteed to arrive in the
  // same delivery. Should probably have a server-side solution for this use cause though.
  latestRealtimeValues = new Map<string, CustomBarsValue>();

  private idMapping: { [key: number]: NamedObjectId; };

  constructor(private yamcs: YamcsService, synchronizer: Synchronizer) {
    this.syncSubscription = synchronizer.sync(() => {
      if (this.plotBuffer.dirty && !this.loading$.getValue()) {
        const plotData = this.plotBuffer.snapshot();
        this.data$.next({
          samples: plotData.samples,
          annotations: plotData.annotations,
          valueRange: plotData.valueRange,
        });
        this.plotBuffer.dirty = false;
      }
    });

    this.plotBuffer = new PlotBuffer(() => {
      this.reloadVisibleRange();
    });
  }

  public addParameter(...parameter: NamedParameterType[]) {
    this.parameters$.next([
      ...this.parameters$.value,
      ...parameter,
    ]);

    if (this.realtimeSubscription) {
      const ids = parameter.map(p => ({ name: p.qualifiedName }));
      this.addToRealtimeSubscription(ids);
    } else {
      this.connectRealtime();
    }
  }

  public removeParameter(qualifiedName: string) {
    const parameters = this.parameters$.value.filter(p => p.qualifiedName !== qualifiedName);
    this.parameters$.next(parameters);
  }

  /**
   * Triggers a new server request for samples.
   * TODO should pass valueRange somehow
   */
  reloadVisibleRange() {
    return this.updateWindow(this.visibleStart, this.visibleStop, [null, null]);
  }


  updateWindow(
    start: Date,
    stop: Date,
    valueRange: DyValueRange,
  ) {
    this.loading$.next(true);
    // Load beyond the visible range to be able to show data
    // when panning.
    const delta = stop.getTime() - start.getTime();
    const loadStart = new Date(start.getTime() - delta);
    const loadStop = new Date(stop.getTime() + delta);

    const promises: Promise<any>[] = [];
    const parametersType: (string|undefined)[] = [];
    for (const parameter of this.parameters$.value) {
      const args = {
        start: loadStart.toISOString(),
        stop: loadStop.toISOString()
      };
      if (isEnumerationType(parameter.type?.engType)) {
        const minRange = Math.round((loadStop.getTime() - loadStart.getTime()) / 6000);
        promises.push(
          this.yamcs.yamcsClient.getParameterRanges(
            this.yamcs.instance!, parameter.qualifiedName, {...args, minRange }
          )
        );
      } else {
        promises.push(
          this.yamcs.yamcsClient.getParameterSamples(
            this.yamcs.instance!, parameter.qualifiedName, {...args, count: 6000}
          )
        );
      }
      parametersType.push(parameter.type?.engType);
      promises.push(this.yamcs.yamcsClient.getAlarmsForParameter(this.yamcs.instance!, parameter.qualifiedName, args));
    }

    const loadPromise = Promise.all(promises);
    this.lastLoadPromise = loadPromise;
    return loadPromise.then(results => {
      // Effectively cancels past requests
      if (this.lastLoadPromise === loadPromise) {
        this.loading$.next(false);
        this.plotBuffer.reset();
        this.latestRealtimeValues.clear();
        this.visibleStart = start;
        this.visibleStop = stop;
        this.minValue = undefined;
        this.maxValue = undefined;
        
        let dySamples = isEnumerationType(parametersType[0]) ? 
          this.processEnumerations(results[0]) : 
          this.processSamples(results[0]);

        const dyAnnotations = this.spliceAlarmAnnotations([] /*results[1] TODO */, dySamples);
        for (let i = 1; i < this.parameters$.value.length; i++) {
          this.mergeSeries(dySamples, this.processSamples(results[2 * i]));
          // const seriesAnnotations = this.spliceAlarmAnnotations(results[2 * i + 1], seriesSamples);
        }
        this.plotBuffer.setArchiveData(dySamples, dyAnnotations);
        this.plotBuffer.setValueRange(valueRange);
        this.lastLoadPromise = null;
      }
    });
  }

  private connectRealtime() {
    const ids = this.parameters$.value.map(parameter => ({ name: parameter.qualifiedName }));
    this.realtimeSubscription = this.yamcs.yamcsClient.createParameterSubscription({
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
      id: ids,
      sendFromCache: false,
      updateOnExpiration: true,
      abortOnInvalid: true,
      action: 'REPLACE',
    }, data => {
      if (data.mapping) {
        this.idMapping = {
          ...this.idMapping,
          ...data.mapping,
        };
      }
      if (data.values && data.values.length) {
        this.processRealtimeDelivery(data.values);
      }
    });
  }

  addToRealtimeSubscription(ids: NamedObjectId[]) {
    this.realtimeSubscription.sendMessage({
      instance: this.yamcs.instance!,
      processor: this.yamcs.processor!,
      id: ids,
      sendFromCache: false,
      updateOnExpiration: true,
      abortOnInvalid: true,
      action: 'ADD',
    });
  }

  /**
   * Emit merged snapsnot (may include values from a previous delivery)
   */
  private processRealtimeDelivery(pvals: ParameterValue[]) {
    for (const pval of pvals) {
      let dyValue: CustomBarsValue = null;
      const value = convertValueToNumber(pval.engValue);
      if (value !== null) {
        if (pval.acquisitionStatus === 'EXPIRED') {
          // We get the last received timestamp.
          // Consider gap to be just after that
          /// t.setTime(t.getTime() + 1); // TODO Commented out because we need identical timestamps in case of multi param plots
          dyValue = null; // Display as gap
        } else if (pval.acquisitionStatus === 'ACQUIRED') {
          dyValue = [value, value, value];
        }
      }
      const id = this.idMapping[pval.numericId];
      this.latestRealtimeValues.set(id.name, dyValue);
    }

    const t = new Date();
    t.setTime(Date.parse(pvals[0].generationTime));

    const dyValues: CustomBarsValue[] = this.parameters$.value.map(parameter => {
      return this.latestRealtimeValues.get(parameter.qualifiedName) || null;
    });

    const sample: any = [t, ...dyValues];
    this.plotBuffer.addRealtimeValue(sample);
  }

  disconnect() {
    this.data$.complete();
    this.loading$.complete();
    if (this.realtimeSubscription) {
      this.realtimeSubscription.cancel();
    }
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }

  private processSamples(samples: Sample[]) {
    const dySamples: DySample[] = [];
    for (const sample of samples) {
      const t = new Date();
      t.setTime(Date.parse(sample['time']));
      if (sample.n > 0) {
        const v = sample['avg'];
        const min = sample['min'];
        const max = sample['max'];

        if (this.minValue === undefined) {
          this.minValue = min;
          this.maxValue = max;
        } else {
          if (this.minValue > min) {
            this.minValue = min;
          }
          if (this.maxValue! < max) {
            this.maxValue = max;
          }
        }
        dySamples.push([t, [min, v, max]]);
      } else {
        dySamples.push([t, null]);
      }
    }
    return dySamples;
  }

  private processEnumerations(ranges: Range[]) {
    const x: DySample[] = [];
    ranges.forEach((range: Range) => {
      const start:Date = new Date(Date.parse(range.timeStart));
      const end:Date = new Date(Date.parse(range.timeStop));
      // if there was multiple values within the range, 
      // we store all the registered values
      if (range.engValues.length > 1) {
        const countValueMap = range.counts.map((count, i) => ({count, stringValue: range.engValues[i].stringValue!}))
        console.log(countValueMap);
        const values: string[] = countValueMap.sort((a, b) => b.stringValue.localeCompare(a.stringValue)).map(v => v.stringValue!);
        x.push([start, [0, values]], [end, [0, values]]);  
      // otherwise, we store the only value
      } else {
        x.push([start, [0, range.engValue.stringValue!]], [end, [0, range.engValue.stringValue!]]);  
      }
    });
    return x;
  }

  private spliceAlarmAnnotations(alarms: Alarm[], dySamples: DySample[]) {
    const dyAnnotations: DyAnnotation[] = [];
    /*for (const alarm of alarms) {
      const t = new Date();
      t.setTime(Date.parse(alarm.triggerValue.generationTime));
      const value = convertValueToNumber(alarm.triggerValue.engValue);
      if (value !== null) {
        const sample: DySample = [t, [value, value, value]];
        const idx = this.findInsertPosition(t, dySamples);
        dySamples.splice(idx, 0, sample);
        dyAnnotations.push({
          series: this.parameters$.value[0].qualifiedName,
          x: t.getTime(),
          shortText: 'A',
          text: 'Alarm triggered at ' + alarm.triggerValue.generationTime,
          tickHeight: 1,
          cssClass: 'annotation',
          tickColor: 'red',
          // attachAtBottom: true,
        });
      }
    }*/
    return dyAnnotations;
  }

  private findInsertPosition(t: Date, dySamples: DySample[]) {
    if (!dySamples.length) {
      return 0;
    }

    for (let i = 0; i < dySamples.length; i++) {
      if (dySamples[i][0] > t) {
        return i;
      }
    }
    return dySamples.length - 1;
  }

  /**
   * Merges two DySample[] series together. This assumes that timestamps between
   * the two series are identical, which is the case if server requests are done
   * with the same date range.
   */
  private mergeSeries(samples1: DySample[], samples2: DySample[]) {
    if (samples1.length !== samples2.length) {
      throw new Error('Cannot merge two sample arrays of unequal length');
    }
    for (let i = 0; i < samples1.length; i++) {
      samples1[i].push(samples2[i][1]);
    }
  }
}
