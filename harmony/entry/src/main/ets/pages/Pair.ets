import router from '@ohos.router';
import { DshApi } from '../net/DshApi';
import { Ink, Radius, Space, FontSize } from '../common/Theme';

@Entry
@Component
struct Pair {
  @State base: string = 'http://10.0.2.2:8443/phone/';
  @State code: string = '';
  @State busy: boolean = false;
  @State error: string = '';

  private async connect(): Promise<void> {
    if (this.base.trim().length === 0 || this.code.trim().length === 0) {
      this.error = '请填写服务器地址与配对码';
      return;
    }
    this.busy = true;
    this.error = '';
    const api = new DshApi();
    try {
      await api.pair(this.base.trim(), this.code.replace(/-/g, '').toUpperCase());
      AppStorage.setOrCreate<string>('dsh_base', api.base);
      AppStorage.setOrCreate<string>('dsh_cookie', api.cookie);
      AppStorage.setOrCreate<string>('dsh_ws', api.wsUrl);
      router.replaceUrl({ url: 'pages/Index' });
    } catch (error) {
      this.error = `${error}`;
    } finally {
      this.busy = false;
    }
  }

  build() {
    Column({ space: Space.md }) {
      Text('连接 DSH').fontSize(FontSize.display).fontWeight(FontWeight.Bold).fontColor(Ink.ink)
      Text('在电脑端「插件 → 手机远程」生成配对码').fontSize(FontSize.caption).fontColor(Ink.muted)
      TextInput({ placeholder: 'https://your-host/phone/', text: this.base })
        .onChange((value: string) => { this.base = value; })
        .backgroundColor(Ink.surface)
        .borderRadius(Radius.chip)
      TextInput({ placeholder: '配对码', text: this.code })
        .onChange((value: string) => { this.code = value; })
        .backgroundColor(Ink.surface)
        .borderRadius(Radius.chip)
      if (this.error.length > 0) {
        Text(this.error).fontSize(FontSize.caption).fontColor(Ink.danger)
      }
      Button(this.busy ? '连接中…' : '连接')
        .width('100%')
        .backgroundColor(Ink.brand)
        .enabled(!this.busy)
        .onClick(() => { this.connect(); })
    }
    .width('100%')
    .height('100%')
    .padding(Space.xl)
    .backgroundColor(Ink.canvas)
    .justifyContent(FlexAlign.Center)
  }
}
