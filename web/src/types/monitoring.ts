export interface CronStatus {
  name: string
  schedule: string
  source: 'pipeline' | 'job'
  lastStatus: string | null
  lastStartedAt: string | null
  lastFinishedAt: string | null
  lastSummary: string | null
}

export interface LiveSourceStatus {
  name: string
  url: string
  status: 'UP' | 'DEGRADED' | 'DOWN' | 'NOT_CONFIGURED'
  httpStatus: number | null
  latencyMs: number
  checkedAt: string
  detail: string | null
}
