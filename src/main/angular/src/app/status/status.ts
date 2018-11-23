export class Status {
  id: string;
  name: string;
  type: string;
  vendor: string;
  runningTime: number;
  remainingMinRunningTime: number;
  remainingMaxRunningTime: number;
  planningRequested: boolean;
  earliestStart: number;
  latestStart: number;
  on: boolean;
  controllable: boolean;
  interruptedSince: number;
  optionalEnergy: boolean;

  public constructor(init?: Partial<Status>) {
    Object.assign(this, init);
  }
}
