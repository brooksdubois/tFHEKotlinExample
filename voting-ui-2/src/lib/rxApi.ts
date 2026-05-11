// lib/rxApi.ts
import { fromFetch } from "rxjs/fetch";
import {firstValueFrom, defer, from, timer, throwError, type Observable, MonoTypeOperatorFunction, retry} from "rxjs";
import { switchMap, timeout as rxTimeout, retryWhen, scan, delayWhen, catchError } from "rxjs/operators";

class HttpError extends Error {
    constructor(public status: number, message: string) { super(message); }
}
type ShouldRetry = (err: unknown, retryIndex: number) => boolean

const backoff = <T>({
        maxRetries = 2, initialDelay = 300, shouldRetry,
    }: { maxRetries?: number; initialDelay?: number; shouldRetry?: ShouldRetry; }
    = {}): MonoTypeOperatorFunction<T> =>
        retry({
            count: maxRetries,
            delay: (err, retryIndex) => {
                if (shouldRetry && !shouldRetry(err, retryIndex)) throw err; // stop retrying
                // retryIndex: 0,1,2,...
                return timer(initialDelay * Math.pow(2, retryIndex));
            },
            resetOnSuccess: true,
        });

export type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  /** Per-call timeout (ms). */
  timeoutMs?: number;
};

export class ApiClient {
  constructor(
    private readonly baseUrl: string,
    private readonly defaultTimeoutMs = 5000
  ) {}

  get$<T>(path: string, opts: Omit<RequestOptions, "method" | "body"> = {}): Observable<T> {
    return this.request$<T>(path, { ...opts, method: "GET" });
  }

  post$<T>(path: string, body: unknown, opts: Omit<RequestOptions, "method"> = {}): Observable<T> {
    return this.request$<T>(path, { ...opts, method: "POST", body });
  }

  async get<T>(path: string, opts?: Omit<RequestOptions, "method" | "body">): Promise<T> {
    return firstValueFrom(this.get$<T>(path, opts));
  }

  async post<T>(path: string, body: unknown, opts?: Omit<RequestOptions, "method">): Promise<T> {
    return firstValueFrom(this.post$<T>(path, body, opts));
  }


  private request$<T>(path: string, opts: RequestOptions): Observable<T> {
      const url = `${this.baseUrl}${path}`;
      const {
          method = "GET",
          body,
          headers = {},
          timeoutMs = this.defaultTimeoutMs,
      } = opts;

      const init: RequestInit = {
          method,
          headers: { "Content-Type": "application/json", ...headers },
          ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      };

      return fromFetch(url, init).pipe(
          rxTimeout({ each: timeoutMs }),
          switchMap(async (res) => {
              if (!res.ok) {
                  const text = await res.text().catch(() => "");
                  throw new HttpError(res.status, `${res.status} ${res.statusText}${text ? `: ${text}` : ""}`);
              }
              const ct = res.headers.get("content-type") || "";
              return (ct.includes("application/json") ? res.json() : res.text()) as Promise<T>;
          }),
          backoff<T>({
              maxRetries: 2,
              initialDelay: 300,
              // retry on timeouts/network errors or 5xx; don't retry 4xx
              shouldRetry: (err) =>
                  !(err instanceof HttpError) || (err.status >= 500 && err.status < 600),
          }),
          catchError(err => throwError(() => err))
      );
  }
}

export const createApi = (baseUrl: string, defaultTimeoutMs?: number) =>
  new ApiClient(baseUrl, defaultTimeoutMs);
