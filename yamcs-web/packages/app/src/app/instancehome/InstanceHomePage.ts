import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { GeneralInfo, Instance, MissionDatabase, TmStatistics } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { AlarmsDataSource } from '../alarms/AlarmsDataSource';
import { AuthService } from '../core/services/AuthService';
import { YamcsService } from '../core/services/YamcsService';
import { User } from '../shared/User';

@Component({
  templateUrl: './InstanceHomePage.html',
  styleUrls: ['./InstanceHomePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstanceHomePage implements OnDestroy {

  instance: Instance;

  private user: User;

  tmstats$ = new BehaviorSubject<TmStatistics[]>([]);
  tmstatsSubscription: Subscription;

  alarmsDataSource: AlarmsDataSource;

  info$: Promise<GeneralInfo>;
  mdb$: Promise<MissionDatabase>;

  constructor(yamcs: YamcsService, authService: AuthService, title: Title) {
    const processor = yamcs.getProcessor();
    this.instance = yamcs.getInstance();
    this.user = authService.getUser()!;
    title.setTitle(this.instance.name);
    yamcs.getInstanceClient()!.getProcessorStatistics().then(response => {
      response.statistics$.pipe(
        filter(stats => stats.yProcessorName === processor.name),
        map(stats => stats.tmstats || []),
      ).subscribe(tmstats => {
        this.tmstats$.next(tmstats);
      });
    });

    this.alarmsDataSource = new AlarmsDataSource(yamcs);
    this.alarmsDataSource.loadAlarms('realtime');

    if (this.showMDB()) {
      this.mdb$ = yamcs.getInstanceClient()!.getMissionDatabase();
    }

    this.info$ = yamcs.yamcsClient.getGeneralInfo();
  }

  showMDB() {
    return this.user.hasSystemPrivilege('GetMissionDatabase');
  }

  ngOnDestroy() {
    if (this.tmstatsSubscription) {
      this.tmstatsSubscription.unsubscribe();
    }
    if (this.alarmsDataSource) {
      this.alarmsDataSource.disconnect();
    }
  }
}