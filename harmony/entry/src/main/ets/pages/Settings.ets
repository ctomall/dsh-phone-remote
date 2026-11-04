import router from '@ohos.router';
import { Ink, Radius, Space, FontSize } from '../common/Theme';

@Entry
@Component
struct Settings {
  @State base: string = '';

  aboutToAppear(): void {
    this.base = AppStorage.get<string>('dsh_base') ?? '未连接';
  }

  @Builder
  row(label: string, value: string) {
    Row() {
      Text(label).fontSize(FontSize.body).fontColor(Ink.ink)
      Blank()
      Text(value).fontSize(FontSize.caption).fontColor(Ink.muted)
    }
    .width('100%')
    .padding(Space.md)
    .backgroundColor(Ink.surface)
    .borderRadius(Radius.chip)
  }

  build() {
    Column({ space: Space.md }) {
      Text('设置').fontSize(FontSize.display).fontWeight(FontWeight.Bold).fontColor(Ink.ink)
      this.row('当前 DSH', this.base)
      this.row('版本', '0.1.0')
      this.row('设计语言', 'Ink')
      Button('重新配对')
        .width('100%')
        .backgroundColor(Ink.surface)
        .fontColor(Ink.brand)
        .onClick(() => { router.pushUrl({ url: 'pages/Pair' }); })
      Blank()
    }
    .width('100%')
    .height('100%')
    .padding(Space.xl)
    .backgroundColor(Ink.canvas)
  }
}
