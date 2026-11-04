import router from '@ohos.router';
import { DshApi } from '../net/DshApi';
import { SessionSummary } from '../model/Models';
import { Ink, Radius, Space, FontSize } from '../common/Theme';

@Entry
@Component
struct Index {
  @State sessions: SessionSummary[] = [];
  @State loading: boolean = true;
  @State tab: number = 0;
  @State query: string = '';
  @State error: string = '';
  private api: DshApi = new DshApi();

  aboutToAppear(): void {
    const base = AppStorage.get<string>('dsh_base') ?? '';
    const cookie = AppStorage.get<string>('dsh_cookie') ?? '';
    const ws = AppStorage.get<string>('dsh_ws') ?? '';
    if (base.length === 0 || cookie.length === 0) {
      router.replaceUrl({ url: 'pages/Pair' });
      return;
    }
    this.api.base = base;
    this.api.cookie = cookie;
    this.api.wsUrl = ws;
    this.reload();
  }

  async reload(): Promise<void> {
    this.loading = true;
    try {
      this.sessions = await this.api.sessions();
      this.error = '';
    } catch (error) {
      this.error = `${error}`;
    } finally {
      this.loading = false;
    }
  }

  runningCount(): number {
    return this.sessions.filter((item) => item.running).length;
  }

  filtered(): SessionSummary[] {
    const query = this.query.trim().toLowerCase();
    if (query.length === 0) {
      return this.sessions;
    }
    return this.sessions.filter((item) =>
      item.title.toLowerCase().includes(query) || item.id.toLowerCase().includes(query) || item.cwd.toLowerCase().includes(query));
  }

  @Builder
  header(title: string, subtitle: string) {
    Row() {
      Column() {
        Text(title).fontSize(FontSize.display).fontWeight(FontWeight.Bold).fontColor(Ink.ink)
        Text(subtitle).fontSize(FontSize.caption).fontColor(Ink.muted).margin({ top: 2 })
      }.alignItems(HorizontalAlign.Start).layoutWeight(1)
      Text('⟳').fontSize(18).fontColor(Ink.muted).onClick(() => { this.reload(); })
    }
    .width('100%')
    .padding({ left: Space.xl, right: Space.xl, top: Space.lg, bottom: Space.md })
  }

  @Builder
  sessionRow(item: SessionSummary) {
    Row({ space: Space.md }) {
      Text(item.title.length > 0 ? item.title.substring(0, 1) : 'D')
        .width(40).height(40)
        .textAlign(TextAlign.Center)
        .fontSize(14).fontWeight(FontWeight.Bold).fontColor(Ink.brand)
        .backgroundColor(Ink.brandSoft)
        .borderRadius(20)
      Column({ space: 2 }) {
        Row({ space: 6 }) {
          Text(item.title.length > 0 ? item.title : item.id.substring(0, 12))
            .fontSize(FontSize.body).fontWeight(FontWeight.Medium).fontColor(Ink.ink)
            .maxLines(1).textOverflow({ overflow: TextOverflow.Ellipsis })
          if (item.running) {
            Text('●').fontSize(8).fontColor(Ink.success)
          }
        }
        Text(item.cwd.length > 0 ? item.cwd : '默认工作区')
          .fontSize(FontSize.meta + 1).fontColor(Ink.muted).maxLines(1)
      }.alignItems(HorizontalAlign.Start).layoutWeight(1)
      Text(this.time(item.updatedAt)).fontSize(FontSize.meta).fontColor(Ink.faint)
    }
    .width('100%')
    .padding(Space.md)
    .backgroundColor(Ink.surface)
    .borderRadius(Radius.card)
    .onClick(() => {
      router.pushUrl({ url: 'pages/Chat', params: { sessionId: item.id, title: item.title } });
    })
  }

  time(value: number): string {
    if (value <= 0) {
      return '--:--';
    }
    const date = new Date(value);
    const hour = date.getHours().toString().padStart(2, '0');
    const minute = date.getMinutes().toString().padStart(2, '0');
    return `${hour}:${minute}`;
  }

  @Builder
  sessionsTab() {
    Column({ space: Space.sm }) {
      this.header('会话', `${this.sessions.length} 个会话 · ${this.runningCount()} 个运行中`)
      TextInput({ placeholder: '搜索标题、ID 或目录', text: this.query })
        .onChange((value: string) => { this.query = value; })
        .backgroundColor(Ink.surface)
        .borderRadius(Radius.chip)
        .margin({ left: Space.xl, right: Space.xl })
      if (this.runningCount() > 0) {
        Row({ space: Space.sm }) {
          Column({ space: 2 }) {
            Text(`${this.runningCount()} 个任务正在运行`).fontSize(15).fontWeight(FontWeight.Medium).fontColor('#FFFFFF')
            Text('点击查看任务进度').fontSize(FontSize.meta).fontColor('#CCFFFFFF')
          }.alignItems(HorizontalAlign.Start).layoutWeight(1)
          Text('›').fontSize(20).fontColor('#FFFFFF')
        }
        .width('100%')
        .padding(Space.lg)
        .margin({ left: Space.xl, right: Space.xl })
        .borderRadius(20)
        .linearGradient({ angle: 90, colors: [['#3347D8', 0], ['#5B78FF', 1]] })
      }
      if (this.error.length > 0) {
        Text(this.error).fontSize(FontSize.caption).fontColor(Ink.danger).margin({ left: Space.xl })
      }
      List({ space: Space.sm }) {
        ForEach(this.filtered(), (item: SessionSummary) => {
          ListItem() { this.sessionRow(item) }
        }, (item: SessionSummary) => item.id)
      }
      .layoutWeight(1)
      .padding({ left: Space.xl, right: Space.xl })
    }
    .layoutWeight(1)
  }

  @Builder
  inboxTab() {
    Column({ space: Space.sm }) {
      this.header('收件箱', `运行中 ${this.runningCount()} 项`)
      List({ space: Space.sm }) {
        ForEach(this.sessions.filter((item) => item.running), (item: SessionSummary) => {
          ListItem() { this.sessionRow(item) }
        }, (item: SessionSummary) => item.id)
      }.layoutWeight(1).padding({ left: Space.xl, right: Space.xl })
    }.layoutWeight(1)
  }

  @Builder
  workspaceTab() {
    Column({ space: Space.sm }) {
      this.header('工作区', `${this.sessions.length} 个会话`)
      List({ space: Space.sm }) {
        ForEach(this.workspaces(), (item: string) => {
          ListItem() {
            Row({ space: Space.md }) {
              Text('📁').fontSize(18)
              Column({ space: 2 }) {
                Text(item.split('/').length > 0 ? item.split('/')[item.split('/').length - 1] : item)
                  .fontSize(FontSize.body).fontColor(Ink.ink)
                Text(item).fontSize(FontSize.meta).fontColor(Ink.muted).maxLines(1)
              }.alignItems(HorizontalAlign.Start).layoutWeight(1)
              Text('›').fontSize(18).fontColor(Ink.faint)
            }
            .width('100%').padding(Space.md).backgroundColor(Ink.surface).borderRadius(Radius.card)
          }
        }, (item: string) => item)
      }.layoutWeight(1).padding({ left: Space.xl, right: Space.xl })
    }.layoutWeight(1)
  }

  workspaces(): string[] {
    const seen: string[] = [];
    this.sessions.forEach((item) => {
      const cwd = item.cwd.length > 0 ? item.cwd : '默认工作区';
      if (!seen.includes(cwd)) {
        seen.push(cwd);
      }
    });
    return seen;
  }

  @Builder
  settingsTab() {
    Column({ space: Space.md }) {
      this.header('设置', AppStorage.get<string>('dsh_base') ?? '')
      Column({ space: Space.sm }) {
        this.settingsRow('当前 DSH', AppStorage.get<string>('dsh_base') ?? '未连接')
        this.settingsRow('版本', '0.1.0')
        this.settingsRow('主题', 'Ink 云白')
      }.padding({ left: Space.xl, right: Space.xl })
      Button('重新配对')
        .width('100%')
        .backgroundColor(Ink.surface)
        .fontColor(Ink.brand)
        .onClick(() => { router.pushUrl({ url: 'pages/Pair' }); })
        .margin({ left: Space.xl, right: Space.xl })
    }.layoutWeight(1)
  }

  @Builder
  settingsRow(label: string, value: string) {
    Row() {
      Text(label).fontSize(FontSize.body).fontColor(Ink.ink)
      Blank()
      Text(value).fontSize(FontSize.caption).fontColor(Ink.muted)
    }
    .width('100%').padding(Space.md).backgroundColor(Ink.surface).borderRadius(Radius.chip)
  }

  @Builder
  bottomNav() {
    Column() {
      Divider().color(Ink.line)
      Row() {
        this.navItem('会话', 0)
        this.navItem('收件箱', 1)
        this.navItem('工作区', 2)
        this.navItem('设置', 3)
      }
      .width('100%')
      .justifyContent(FlexAlign.SpaceAround)
      .padding({ top: Space.sm, bottom: Space.sm })
      .backgroundColor(Ink.surface)
    }
  }

  @Builder
  navItem(label: string, index: number) {
    Column({ space: 2 }) {
      Text(this.icon(index)).fontSize(18).fontColor(this.tab === index ? Ink.brand : Ink.faint)
      Text(label).fontSize(FontSize.meta).fontColor(this.tab === index ? Ink.brand : Ink.faint)
    }
    .padding(Space.xs)
    .onClick(() => { this.tab = index; })
  }

  icon(index: number): string {
    if (index === 0) return '💬';
    if (index === 1) return '📥';
    if (index === 2) return '📁';
    return '⚙️';
  }

  build() {
    Column() {
      if (this.tab === 0) {
        this.sessionsTab()
      } else if (this.tab === 1) {
        this.inboxTab()
      } else if (this.tab === 2) {
        this.workspaceTab()
      } else {
        this.settingsTab()
      }
      this.bottomNav()
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Ink.canvas)
  }
}
