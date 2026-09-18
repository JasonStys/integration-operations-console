/**
 * File: client.ts
 * Purpose: Small typed HTTP adapter built on OpenAPI-generated schema declarations.
 * Symbols: exported schema aliases, OperationsClient contract, HttpOperationsClient,
 * and apiClient instance; exact lines are generated in docs/code-index.md.
 * Variables: baseUrl is immutable; mutation headers are explicit demo-only role metadata.
 */
import type { components } from './schema';

export type ConnectorDescriptor = components['schemas']['ConnectorDescriptor'];
export type ConnectorAccount = components['schemas']['ConnectorAccount'];
export type CreateAccountRequest = components['schemas']['CreateAccountRequest'];
export type SubmitJobRequest = components['schemas']['SubmitJobRequest'];
export type SyncJob = components['schemas']['SyncJob'];
export type JobStatus = components['schemas']['JobStatus'];
export type FailurePlan = components['schemas']['FailurePlan'];
export type AuditEvent = components['schemas']['AuditEvent'];
export type Dashboard = components['schemas']['Dashboard'];
export type JobPage = components['schemas']['JobPage'];
export type AccountMutationResult = components['schemas']['AccountMutationResult'];
export type JobMutationResult = components['schemas']['JobMutationResult'];
export type RunNextResult = components['schemas']['RunNextResult'];

/** Operations required by the React view and replaced with fakes in component tests. */
export interface OperationsClient {
  connectors(): Promise<ConnectorDescriptor[]>;
  accounts(): Promise<ConnectorAccount[]>;
  dashboard(): Promise<Dashboard>;
  createAccount(request: CreateAccountRequest): Promise<AccountMutationResult>;
  submitJob(request: SubmitJobRequest, idempotencyKey: string): Promise<JobMutationResult>;
  runNext(): Promise<RunNextResult>;
  runJob(id: string): Promise<SyncJob>;
  cancelJob(id: string): Promise<SyncJob>;
  replayJob(id: string): Promise<SyncJob>;
  audit(id: string): Promise<AuditEvent[]>;
}

/** Fetch-based implementation with consistent JSON/problem handling. */
export class HttpOperationsClient implements OperationsClient {
  private readonly baseUrl: string;

  public constructor(baseUrl = '') {
    this.baseUrl = baseUrl.replace(/\/$/, '');
  }

  public connectors(): Promise<ConnectorDescriptor[]> {
    return this.request('/api/v1/connectors');
  }

  public accounts(): Promise<ConnectorAccount[]> {
    return this.request('/api/v1/accounts');
  }

  public dashboard(): Promise<Dashboard> {
    return this.request('/api/v1/dashboard');
  }

  public createAccount(request: CreateAccountRequest): Promise<AccountMutationResult> {
    return this.mutate('/api/v1/accounts', request);
  }

  public submitJob(request: SubmitJobRequest, idempotencyKey: string): Promise<JobMutationResult> {
    return this.mutate('/api/v1/jobs', request, { 'Idempotency-Key': idempotencyKey });
  }

  public runNext(): Promise<RunNextResult> {
    return this.mutate('/api/v1/jobs/run-next');
  }

  public runJob(id: string): Promise<SyncJob> {
    return this.mutate(`/api/v1/jobs/${encodeURIComponent(id)}/run`);
  }

  public cancelJob(id: string): Promise<SyncJob> {
    return this.mutate(`/api/v1/jobs/${encodeURIComponent(id)}/cancel`);
  }

  public replayJob(id: string): Promise<SyncJob> {
    return this.mutate(`/api/v1/jobs/${encodeURIComponent(id)}/replay`);
  }

  public audit(id: string): Promise<AuditEvent[]> {
    return this.request(`/api/v1/jobs/${encodeURIComponent(id)}/audit`);
  }

  private mutate<T>(path: string, body?: unknown, headers: HeadersInit = {}): Promise<T> {
    return this.request(path, {
      method: 'POST',
      headers: {
        'X-Demo-Role': 'operator',
        'X-Demo-Actor': 'web-console',
        ...headers,
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
  }

  private async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const response = await fetch(`${this.baseUrl}${path}`, {
      ...options,
      headers: {
        Accept: 'application/json',
        ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...options.headers,
      },
    });
    if (!response.ok) {
      const problem = (await response.json().catch(() => null)) as {
        detail?: string;
        title?: string;
      } | null;
      throw new Error(
        problem?.detail ?? problem?.title ?? `Request failed with ${response.status}`,
      );
    }
    return (await response.json()) as T;
  }
}

export const apiClient: OperationsClient = new HttpOperationsClient();
