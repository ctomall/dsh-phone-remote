import router from '@ohos.router';
import webSocket from '@ohos.net.webSocket';
import { DshApi } from '../net/DshApi';
import { ChatMessage } from '../model/Models';
import { Ink, Radius, Space, FontSize } from '../common/Theme';

@Entry
@Component
struct Chat {
  @State title: string = '会话';
  @State messages: ChatMessage[] = [];
  @State input: string = '';
  @State status: string = '连接中';
  @State error: string = '';
  private sessionId: string = '';
  private socket: webSocket.WebSocket | null = null;
  private streamKey: string = '';
  private api: DshApi = new DshApi();

  aboutToAppear(): void {
    const params = router.getParams() as Record<string, Object>;
    this.sessionId = (params['sessionId'] as string) ?? '';
    this.title = ((params['title'] as string) ?? '').length > 0 ? (params['title'] as string) : '会话';
    this.api.base = AppStorage.get<string>('dsh_base') ?? '';
    this.api.cookie = AppStorage.get<string>('dsh_cookie') ?? '';
    this.api.wsUrl = AppStorage.get<string>('dsh_ws') ?? '';
    this.connect();
  }

  aboutToDisappear(): void {
    this.socket?.close({ code: 1000, reason: 'leave' });
  }

  connect(): void {
    this.status = '连接中';
    this.socket = this.api.follow(this.sessionId, (frame: string) => this.onFrame(frame));
    this.status = '已连接';
  }

  onFrame(raw: string): void {
    try {
      const value = JSON.parse(raw) as Record<string, Object>;
      const type = value['type'] as string;
      if (type === 'assistant-stream') {
        const frame = value['frame'] as Record<string, Object>;
        if ((frame['type'] as string) === 'chunk') {
          const chunk = frame['chunk'] as Record<string, Object>;
          if ((chunk['type'] as string) === 'text-delta') {
            this.appendText(chunk['text'] as string);
          }
        }
      } else if (type === 'event') {
        const event = value['event'] as Record<string, Object>;
        if ((event['type'] as string) === 'assistant/message') {
          const data = event['data'] as Record<string, Object>;
          const message = data['message'] as Record<string, Object>;
          const text = this.messageText(message);
          if (text.length > 0) {
            const key = `a-${Date.now()}`;
            this.messages.push({ key, role: 'assistant', text } as ChatMessage);
            this.streamKey = '';
          }
        }
      }
    } catch (error) {
      this.error = `${error}`;
    }
  }

  messageText(message: Record<string, Object>): string {
    const content = message['content'] as Array<Record<string, Object>>;
    if (!content) {
      return '';
    }
    let text = '';
    content.forEach((block: Record<string, Object>) => {
      if ((block['type'] as string) === 'text') {
        text += (block['text'] as string) ?? '';
      }
    });
    return text;
  }

  appendText(delta: string): void {
    if (delta.length === 0) {
      return;
    }
    if (this.streamKey.length === 0) {
      this.streamKey = `s-${Date.now()}`;
      this.messages.push({ key: this.streamKey, role: 'assistant', text: delta } as ChatMessage);
      return;
    }
    const index = this.messages.findIndex((item: ChatMessage) => item.key === this.streamKey);
    if (index >= 0) {
      this.messages[index].text += delta;
    }
  }

  @Builder
  bubble(item: ChatMessage) {
    Row() {
      if (item.role === 'user') {
        Blank()
        Text(item.text)
          .fontSize(FontSize.body)
          .fontColor(Ink.ink)
          .padding(Space.md)
          .backgroundColor(Ink.brandSoft)
          .borderRadius(Radius.bubble)
          .constraintSize({ maxWidth: '78%' })
      } else {
        Column({ space: 6 }) {
          Text('DSH').fontSize(FontSize.meta).fontColor(Ink.brand)
          Text(item.text).fontSize(FontSize.body).fontColor(Ink.ink2)
        }
        .alignItems(HorizontalAlign.Start)
        .padding(Space.md)
        .backgroundColor(Ink.surface)
        .borderRadius(Radius.bubble)
        .constraintSize({ maxWidth: '88%' })
        Blank()
      }
    }
    .width('100%')
  }

  build() {
    Column() {
      Row({ space: Space.sm }) {
        Text('‹').fontSize(24).fontColor(Ink.ink).onClick(() => { router.back(); })
        Column({ space: 1 }) {
          Text(this.title).fontSize(FontSize.title).fontWeight(FontWeight.Medium).fontColor(Ink.ink).maxLines(1)
          Text(`已连接 · ${this.status}`).fontSize(FontSize.meta).fontColor(Ink.muted)
        }.alignItems(HorizontalAlign.Start).layoutWeight(1)
      }
      .width('100%')
      .padding({ left: Space.lg, right: Space.lg, top: Space.md, bottom: Space.md })

      if (this.error.length > 0) {
        Text(this.error).fontSize(FontSize.caption).fontColor(Ink.danger).padding({ left: Space.lg })
      }

      List({ space: Space.md }) {
        ForEach(this.messages, (item: ChatMessage) => {
          ListItem() { this.bubble(item) }
        }, (item: ChatMessage) => item.key)
      }
      .layoutWeight(1)
      .padding({ left: Space.lg, right: Space.lg })

      Row({ space: Space.sm }) {
        Text('+').fontSize(20).fontColor(Ink.muted)
        TextInput({ placeholder: '跟进…', text: this.input })
          .onChange((value: string) => { this.input = value; })
          .layoutWeight(1)
          .backgroundColor(Ink.surface)
          .borderRadius(Radius.field)
        Text('↑')
          .width(40).height(40)
          .textAlign(TextAlign.Center)
          .fontSize(18)
          .fontColor('#FFFFFF')
          .backgroundColor(Ink.brand)
          .borderRadius(20)
          .onClick(() => { this.send(); })
      }
      .width('100%')
      .padding(Space.md)
      .backgroundColor(Ink.canvas)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Ink.canvas)
  }

  async send(): Promise<void> {
    const text = this.input.trim();
    if (text.length === 0) {
      return;
    }
    this.input = '';
    this.messages.push({ key: `u-${Date.now()}`, role: 'user', text } as ChatMessage);
    try {
      await this.api.prompt(this.sessionId, text);
    } catch (error) {
      this.error = `${error}`;
    }
  }
}
