import http from '@ohos.net.http';
import webSocket from '@ohos.net.webSocket';
import util from '@ohos.util';
import { SessionSummary } from '../model/Models';

interface RpcEnvelope {
  type: string;
  rpcId: string;
  method: string;
  payload: Object;
}

interface SessionItem {
  sessionId?: string;
  updatedAt?: number;
  running?: boolean;
  blank?: boolean;
  cwd?: string;
  projections?: SessionProjections;
}

interface SessionProjections {
  values?: SessionProjectionValues;
}

interface SessionProjectionValues {
  title?: string;
}

interface SessionListValue {
  items?: SessionItem[];
}

interface RpcResult {
  ok?: boolean;
  value?: Object;
}

interface RpcResponse {
  result?: RpcResult;
  error?: Object;
}

export class DshApiError extends Error {
  constructor(message: string) {
    super(message);
  }
}

export class DshApi {
  base: string = '';
  wsUrl: string = '';
  cookie: string = '';

  private absolute(path: string): string {
    if (path.startsWith('http')) {
      return path;
    }
    const root = this.base.endsWith('/') ? this.base : this.base + '/';
    return root + (path.startsWith('/') ? path.substring(1) : path);
  }

  private cookieHeader(): Record<string, string> {
    return { 'Cookie': this.cookie };
  }

  private async send(request: http.HttpRequest, url: string, options: http.HttpRequestOptions): Promise<http.HttpResponse> {
    return await request.request(url, options);
  }

  /** Pair with a one-time code; stores the session cookie on success. */
  async pair(base: string, code: string): Promise<void> {
    this.base = base.endsWith('/') ? base : base + '/';
    const request = http.createHttp();
    try {
      let response = await this.send(request, this.absolute('pair'), {
        method: http.RequestMethod.POST,
        header: { 'Content-Type': 'application/x-www-form-urlencoded' },
        extraData: `code=${encodeURIComponent(code)}&name=${encodeURIComponent('Harmony Native')}&next=`,
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 15000,
        readTimeout: 20000,
      });
      this.absorbCookies(response);
      let hops = 0;
      while ((response.responseCode === 301 || response.responseCode === 302 || response.responseCode === 303) && hops < 6) {
        const headers = response.header as Record<string, string>;
        const location = headers['location'];
        if (!location) {
          break;
        }
        response = await this.send(request, this.absolute(location), {
          method: http.RequestMethod.GET,
          header: this.cookieHeader(),
          expectDataType: http.HttpDataType.STRING,
          connectTimeout: 15000,
          readTimeout: 20000,
        });
        this.absorbCookies(response);
        hops += 1;
      }
      if (response.responseCode !== 200 && response.responseCode !== 204) {
        throw new DshApiError(`配对失败：HTTP ${response.responseCode}`);
      }
      if (this.cookie.length === 0) {
        throw new DshApiError('配对成功但未拿到会话凭据');
      }
      const host = this.base.replace('https://', '').replace('http://', '').replace(/\/$/, '');
      this.wsUrl = `ws://${host}api/remote.mux`;
    } finally {
      request.destroy();
    }
  }

  private absorbCookies(response: http.HttpResponse): void {
    const cookies = (response.cookies ?? '').trim();
    if (cookies.length > 0) {
      this.cookie = cookies;
      return;
    }
    const headers = response.header as Record<string, string>;
    const raw = headers['set-cookie'];
    if (raw && raw.length > 0) {
      this.cookie = raw.split(',').map((item: string) => item.split(';')[0].trim()).join('; ');
    }
  }

  private async call(method: string, args: Object): Promise<Object> {
    const request = http.createHttp();
    try {
      const envelope: RpcEnvelope = {
        type: 'client-request',
        rpcId: util.generateRandomUUID(true),
        method,
        payload: { args },
      };
      const response = await this.send(request, this.absolute(`api/${method}`), {
        method: http.RequestMethod.POST,
        header: {
          'Content-Type': 'application/json',
          'Cookie': this.cookie,
        },
        extraData: JSON.stringify(envelope),
        expectDataType: http.HttpDataType.STRING,
        connectTimeout: 15000,
        readTimeout: 30000,
      });
      if (response.responseCode === 401 || response.responseCode === 403) {
        throw new DshApiError('登录已失效，请重新配对');
      }
      if (response.responseCode !== 200) {
        throw new DshApiError(`${method} 失败：HTTP ${response.responseCode}`);
      }
      const text = response.result as string;
      const parsed = JSON.parse(text) as RpcResponse;
      if (parsed.result && parsed.result.ok === false) {
        throw new DshApiError(`${method} 返回错误`);
      }
      return parsed.result?.value ?? {};
    } finally {
      request.destroy();
    }
  }

  /** Send a plain-text prompt to a session (attachments: next iteration). */
  async prompt(sessionId: string, text: string): Promise<void> {
    const content: Object[] = [{ type: 'text', text }];
    await this.call('session/prompt', {
      request: {
        sessionId,
        content,
      },
    });
  }

  async sessions(): Promise<SessionSummary[]> {
    const value = await this.call('session/list', { _request: {} });
    const list = value as SessionListValue;
    const items = list.items ?? [];
    return items
      .filter((item: SessionItem) => (item.sessionId ?? '').length > 0)
      .map((item: SessionItem) => {
        const title = item.projections?.values?.title ?? '';
        return {
          id: item.sessionId ?? '',
          title,
          updatedAt: item.updatedAt ?? 0,
          running: item.running ?? false,
          cwd: item.cwd ?? '',
        } as SessionSummary;
      });
  }

  /** Open the follow stream for one session; returns the socket for cleanup. */
  follow(sessionId: string, onFrame: (frame: string) => void): webSocket.WebSocket {
    const socket = webSocket.createWebSocket();
    socket.on('message', (error: Object, data: string | ArrayBuffer) => {
      if (typeof data === 'string') {
        onFrame(data);
      }
    });
    socket.connect(this.wsUrl, { header: this.cookieHeader() }, (error: Object) => {
      if (!error) {
        const streamId = util.generateRandomUUID(true);
        socket.send(JSON.stringify({
          type: 'open',
          streamId,
          endpoint: 'session/follow',
          payload: { args: { request: { sessionId } } },
        }));
        socket.send(JSON.stringify({
          type: 'item',
          streamId,
          value: { type: 'subscribe' },
        }));
      }
    });
    return socket;
  }
}
