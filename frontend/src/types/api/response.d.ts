export interface HttpResponse<T = unknown> {
  code: number
  msg: string
  data?: T
}
