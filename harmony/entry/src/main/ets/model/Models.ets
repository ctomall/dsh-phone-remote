export interface SessionSummary {
  id: string;
  title: string;
  updatedAt: number;
  running: boolean;
  cwd: string;
}

export interface ChatMessage {
  key: string;
  role: string; // user | assistant | tool | notice
  text: string;
  reasoning?: string;
  toolName?: string;
  toolStatus?: string;
}

export interface Question {
  id: string;
  header?: string;
  question: string;
}
