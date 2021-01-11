import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { DownloadParameterValuesOptions, GetParameterValuesOptions } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { Option } from '../../shared/forms/Select';
import * as utils from '../../shared/utils';
import { ParameterDataDataSource } from './ParameterDataDataSource';

const defaultInterval = 'PT1H';

@Component({
  templateUrl: './ParameterDataTab.html',
  styleUrls: ['./ParameterDataTab.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterDataTab {

  qualifiedName: string;

  intervalOptions: Option[] = [
    { id: 'PT1H', label: 'Last Hour' },
    { id: 'PT6H', label: 'Last 6 Hours' },
    { id: 'P1D', label: 'Last 24 Hours' },
    { id: 'NO_LIMIT', label: 'No Limit' },
    { id: 'CUSTOM', label: 'Custom' },
  ];

  validStart: Date | null;
  validStop: Date | null;

  // Same as filter.interval but only updates after 'apply' in case of custom dates
  // This allows showing visual indicators for the visible data set before a custom
  // range is actually applied.
  appliedInterval: string;

  filter = new FormGroup({
    interval: new FormControl(defaultInterval),
    customStart: new FormControl(null),
    customStop: new FormControl(null),
  });

  dataSource: ParameterDataDataSource;
  downloadURL$ = new BehaviorSubject<string | null>(null);

  constructor(route: ActivatedRoute, readonly yamcs: YamcsService) {
    this.qualifiedName = route.parent!.snapshot.paramMap.get('qualifiedName')!;
    this.dataSource = new ParameterDataDataSource(yamcs, this.qualifiedName);

    this.validStop = yamcs.getMissionTime();
    this.validStart = utils.subtractDuration(this.validStop, defaultInterval);
    this.appliedInterval = defaultInterval;
    this.loadData();

    this.filter.get('interval')!.valueChanges.forEach(nextInterval => {
      if (nextInterval === 'CUSTOM') {
        const customStart = this.validStart || this.yamcs.getMissionTime();
        const customStop = this.validStop || this.yamcs.getMissionTime();
        this.filter.get('customStart')!.setValue(utils.toISOString(customStart));
        this.filter.get('customStop')!.setValue(utils.toISOString(customStop));
      } else if (nextInterval === 'NO_LIMIT') {
        this.validStart = null;
        this.validStop = null;
        this.appliedInterval = nextInterval;
        this.loadData();
      } else {
        this.validStop = yamcs.getMissionTime();
        this.validStart = utils.subtractDuration(this.validStop, nextInterval);
        this.appliedInterval = nextInterval;
        this.loadData();
      }
    });
  }

  jumpToNow() {
    const interval = this.filter.value['interval'];
    if (interval === 'NO_LIMIT') {
      // NO_LIMIT may include future data under erratic conditions. Reverting
      // to the default interval is more in line with the wording 'jump to now'.
      this.filter.get('interval')!.setValue(defaultInterval);
    } else {
      this.validStop = this.yamcs.getMissionTime();
      this.validStart = utils.subtractDuration(this.validStop, interval);
      this.loadData();
    }
  }

  applyCustomDates() {
    this.validStart = utils.toDate(this.filter.value['customStart']);
    this.validStop = utils.toDate(this.filter.value['customStop']);
    this.appliedInterval = 'CUSTOM';
    this.loadData();
  }

  /**
   * Loads the first page of data within validStart and validStop
   */
  loadData() {
    const options: GetParameterValuesOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      options.stop = this.validStop.toISOString();
    }

    const dlOptions: DownloadParameterValuesOptions = {
      parameters: [this.qualifiedName]
    };
    if (this.validStart) {
      dlOptions.start = this.validStart.toISOString();
    }
    if (this.validStop) {
      dlOptions.stop = this.validStop.toISOString();
    }

    this.dataSource.loadParameterValues(options).then(pvals => {
      const downloadURL = this.yamcs.yamcsClient.getParameterValuesDownloadURL(this.yamcs.instance!, dlOptions);
      this.downloadURL$.next(downloadURL);
    });
  }

  /**
   * Loads the next page of data starting at where the previous page was cut off.
   * This not 100% waterproof as data may have arrived with generation time between
   * the last visible data and the offscreen record. This is unlikely to cause
   * practical problems.
   */
  loadMoreData() {
    const options: GetParameterValuesOptions = {};
    if (this.validStart) {
      options.start = this.validStart.toISOString();
    }
    this.dataSource.loadMoreData(options);
  }
}
