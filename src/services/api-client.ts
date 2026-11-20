import {readJsonResponse} from './response';

const loopback = ['localhost', '127.0.0.1', '[::1]'];
const timeoutMs = 12_000;

export function resolveApiUrl(configured: string, development: boolean, webOrigin?: string): string {
 if (!configured) throw new Error('Set EXPO_PUBLIC_API_URL to connect to CartIQ.');
 let url: URL;
 try {
  url = new URL(configured, webOrigin);
 } catch {
  throw new Error('The CartIQ server URL is invalid.');
 }
 if (!['http:', 'https:'].includes(url.protocol) || !url.hostname || url.username || url.password || url.search || url.hash) {
  throw new Error('The CartIQ server URL is invalid.');
 }
 const origin = webOrigin ? new URL(webOrigin) : null;
 const localWeb = origin?.protocol === 'http:' && loopback.includes(origin.hostname) && loopback.includes(url.hostname);
 if (url.protocol !== 'https:' && !development && !localWeb) {
  throw new Error('A secure server connection is required.');
 }
 const path = url.pathname.replace(/\/+$/, '');
 const host = url.protocol === 'http:' && localWeb
  ? origin.hostname + (url.port ? ':' + url.port : '')
  : url.host;
 return `${url.protocol}//${host}${path.endsWith('/api') ? path : path + '/api'}`;
}

export class ApiError extends Error {
 constructor(readonly status: number, message: string) {
  super(message);
 }
}

export async function fetchJson<T>(
 url: string, options: RequestInit, token: string | null = null, deadline = timeoutMs,
): Promise<T> {
 const controller = new AbortController();
 const cancel = () => controller.abort();
 const {signal, ...request} = options;
 if (signal?.aborted) cancel();
 else signal?.addEventListener('abort', cancel, {once: true});
 const timer = setTimeout(cancel, deadline);
 try {
  const headers = new Headers(request.headers);
  if (request.body != null && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(url, {...request, credentials: 'include', signal: controller.signal, headers});
  if (response.ok) return await readJsonResponse<T>(response);
  let message = '';
  try {
   const body = await readJsonResponse<{message?: string}>(response);
   if (typeof body?.message === 'string') message = body.message;
  } catch (error) {
   if (controller.signal.aborted) throw error;
  }
  const fallback = response.status === 403 ? 'You do not have access to this action.' :
   response.status >= 500 ? 'The service is temporarily unavailable. Please try again.' :
   'Please check your details and try again.';
  throw new ApiError(response.status, message || fallback);
 } catch (error) {
  if (signal?.aborted) throw error;
  if (controller.signal.aborted) throw new Error('The connection timed out. Please try again.');
  if (error instanceof TypeError) throw new Error('Could not connect to CartIQ. Check your connection and try again.');
  throw error;
 } finally {
  clearTimeout(timer);
  signal?.removeEventListener('abort', cancel);
 }
}
