export type TokenResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
  userId: string;
  tenantId: string;
  email: string;
};

export type StationProfile = {
  callsign: string;
  gridsquare: string;
  name: string;
  rig: string;
  dxcc: string;
  defaultRstSent: string;
  defaultRstRcvd: string;
  defaultBand: string;
  defaultMode: string;
  defaultTxpwr: string;
};

export type DbConfig = {
  host: string;
  port: number;
  database: string;
  username: string;
  sslEnabled: boolean;
  passwordSet: boolean;
  lastTestOkAt: string | null;
};

export type DbConfigRequest = {
  host: string;
  port: number;
  database: string;
  username: string;
  password?: string;
  sslEnabled: boolean;
};

export type LookupCredentials = {
  qrzUser: string;
  qrzPassword?: string | null;
  hamqthUser: string;
  hamqthPassword?: string | null;
  clublogApiKey?: string | null;
  qrzPasswordSet: boolean;
  hamqthPasswordSet: boolean;
  clublogApiKeySet: boolean;
};

export type Qso = {
  qsoid?: number | null;
  callsign: string;
  band: string;
  mode: string;
  qsodate: string;
  freq: number;
  freqrx: number;
  rstsent: string;
  rstrcvd: string;
  name: string;
  address: string;
  qth: string;
  country: string;
  dxcc: number;
  cqzone?: number | null;
  ituzone?: number | null;
  gridsquare: string;
  cont: string;
  comment: string;
  notes: string;
  txpwr?: number | null;
  propmode: string;
  contestid: string;
  satmode: string;
  satname: string;
  satelliteqso: number;
  stationcallsign: string;
  mygridsquare: string;
  myname: string;
  myrig: string;
  mycountry: string;
  mydxcc?: number | null;
  mylat?: number | null;
  mylon?: number | null;
  operator: string;
  bandrx: string;
  lat?: number | null;
  lon?: number | null;
  distance?: number | null;
  sotaRef: string;
  iota: string;
  potaRef: string;
  wwffRef: string;
  cotaRef: string;
  programid?: string;
  programversion?: string;
};

export type LogFilter = {
  callsign?: string;
  band?: string;
  mode?: string;
  dateFrom?: string;
  dateTo?: string;
  country?: string;
  dxcc?: string;
  sotaRef?: string;
  iota?: string;
  potaRef?: string;
  wwffRef?: string;
  cotaRef?: string;
};

export function emptyQso(defaults?: Partial<Qso>): Qso {
  const now = new Date();
  const iso = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 19);
  return {
    callsign: "",
    band: defaults?.band ?? "20m",
    mode: defaults?.mode ?? "SSB",
    qsodate: iso,
    freq: 14.2,
    freqrx: 0,
    rstsent: defaults?.rstsent ?? "59",
    rstrcvd: defaults?.rstrcvd ?? "59",
    name: "",
    address: "",
    qth: "",
    country: "",
    dxcc: 0,
    gridsquare: "",
    cont: "",
    comment: "",
    notes: "",
    txpwr: null,
    propmode: "",
    contestid: "",
    satmode: "",
    satname: "",
    satelliteqso: 0,
    stationcallsign: defaults?.stationcallsign ?? "",
    mygridsquare: defaults?.mygridsquare ?? "",
    myname: defaults?.myname ?? "",
    myrig: defaults?.myrig ?? "",
    mycountry: "",
    operator: "",
    bandrx: "",
    sotaRef: "",
    iota: "",
    potaRef: "",
    wwffRef: "",
    cotaRef: "",
    ...defaults,
  };
}
