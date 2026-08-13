import type { TelegramConfig } from "../../../api/client";

type Props = {
  value: TelegramConfig;
  onChange: (next: TelegramConfig) => void;
};

export function TelegramSettingsForm({ value, onChange }: Props) {
  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card-title" style={{ marginBottom: 12 }}>Telegram Bot</div>
      <p style={{ fontSize: 13, color: "var(--color-muted)", marginBottom: 12 }}>
        Delivery uses <code>bot_token</code> plus a <code>chat_id</code> (channel default or per-recipient <code>telegramChatId</code>).
      </p>
      <div className="filter-bar">
        <label>
          <input
            type="checkbox"
            checked={value.enabled}
            onChange={(e) => onChange({ ...value, enabled: e.target.checked })}
          />{" "}
          Enable Telegram delivery
        </label>
      </div>
      <div className="filter-bar">
        <input
          className="filter-input"
          placeholder="Telegram API Base"
          value={value.apiBase}
          onChange={(e) => onChange({ ...value, apiBase: e.target.value })}
        />
        <input
          className="filter-input"
          placeholder="Parse mode (Markdown/HTML)"
          value={value.parseMode}
          onChange={(e) => onChange({ ...value, parseMode: e.target.value })}
        />
      </div>
      <div className="filter-bar">
        <input
          className="filter-input"
          style={{ flex: 2 }}
          placeholder="Bot token (bot_token)"
          value={value.botToken ?? ""}
          onChange={(e) => onChange({ ...value, botToken: e.target.value })}
        />
        <input
          className="filter-input"
          placeholder="Default chat id (optional)"
          value={value.chatId ?? ""}
          onChange={(e) => onChange({ ...value, chatId: e.target.value })}
        />
      </div>
    </div>
  );
}
