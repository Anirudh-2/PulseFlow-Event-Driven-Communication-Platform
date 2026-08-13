import axios from "axios";
import { resolveAccessToken } from "../app/AuthContext";

const isProd = import.meta.env.PROD;

export const api = axios.create({
  baseURL: isProd
    ? (import.meta.env.VITE_API_URL ?? "/api/v1")
    : "/api/v1",
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use(async (config) => {
  const token = await resolveAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      error.response?.data?.message ??
      error.response?.data?.error ??
      error.message ??
      "An unexpected error occurred";
    return Promise.reject(new Error(message));
  }
);

export interface Notification {
  id: string;
  tenantId: string;
  title: string;
  body: string;
  type: string;
  priority: string;
  sourceService?: string;
  sourceEventId?: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
  /** Normalized event type from the notification row. */
  eventType?: string | null;
  /** Inbox state when listing with userId: UNREAD, READ, ACKNOWLEDGED. */
  status?: string | null;
  /** Row lifecycle e.g. ACTIVE. */
  lifecycleStatus?: string | null;
}

export interface NotificationRule {
  id: string;
  tenantId: string;
  name: string;
  targetRole: string;
  roleName?: string;
  notificationType?: string;
  /** Event-type string for routing (e.g. LEAVE_APPROVED); optional for legacy rules. */
  eventType?: string;
  integrationSourceId?: string;
  channels: string[];
  priorityOverride?: string;
  evaluationOrder: number;
  evalOrder?: number;
  active: boolean;
  isActive?: boolean;
  conditions?: Record<string, unknown>;
  conditionsJsonlogic?: Record<string, unknown>;
  createdAt: string;
}

export interface DeliveryLog {
  id: string;
  notificationId: string;
  channel: string;
  status: string;
  attemptCount: number;
  deliveredAt?: string;
  errorMessage?: string;
  createdAt: string;
}

export interface AuditLog {
  id: string;
  notificationId?: string;
  action: string;
  actorUserId?: string;
  correlationId?: string;
  tenantId: string;
  metadata?: Record<string, unknown>;
  occurredAt: string;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface SecretRef {
  provider: "azure-key-vault" | "aws-secrets-manager" | "hashicorp-vault" | "other";
  path: string;
  key: string;
  version?: string;
}

export interface TeamsConfig {
  enabled: boolean;
  /** Teams Incoming Webhook URL used by delivery (`webhook_url`). */
  webhookUrl?: string;
  /** @deprecated Graph path retained for legacy configs only. */
  clientId?: string;
  tenantId?: string;
  botUserId?: string;
  clientSecretRef?: SecretRef;
}

export interface SmtpConfig {
  enabled: boolean;
  host: string;
  port: number;
  username: string;
  fromAddress: string;
  startTls: boolean;
  auth: boolean;
  passwordRef?: SecretRef;
}

export interface WebhookSecurityConfig {
  mode: "API_KEY" | "JWT";
  apiKeyHeader: string;
  apiKeyRef?: SecretRef;
  jwtIssuer?: string;
  jwtAudience?: string;
}

export interface HrmsMappingConfig {
  defaultTenantId: string;
  sourceServiceName: string;
  userIdentifierStrategy: "AAD_ID_FIRST" | "EMAIL_ONLY" | "AAD_ONLY";
  eventTypeMap: Record<string, string>;
}

export interface TemplateConfig {
  teams: {
    titleTemplate: string;
    bodyTemplate: string;
  };
  email: {
    subjectTemplate: string;
    bodyTemplate: string;
  };
}

export interface TelegramConfig {
  enabled: boolean;
  apiBase: string;
  parseMode: string;
  botToken?: string;
  /** Optional default chat id; recipients may override via telegramChatId. */
  chatId?: string;
  botTokenRef?: SecretRef;
}

export interface IntegrationConfig {
  teams: TeamsConfig;
  smtp: SmtpConfig;
  webhookSecurity: WebhookSecurityConfig;
  hrmsMapping: HrmsMappingConfig;
  templates: TemplateConfig;
  telegram: TelegramConfig;
  updatedAt?: string;
  updatedBy?: string;
}

export interface RuleConfigInput {
  name: string;
  roleName: string;
  notificationType?: string;
  eventType?: string;
  integrationSourceId?: string;
  channels: string[];
  evalOrder: number;
  isActive: boolean;
  conditions?: Record<string, unknown>;
  conditionsJsonlogic?: Record<string, unknown>;
}

export interface IntegrationSourceRow {
  id: string;
  tenantId: string;
  sourceKey: string;
  displayName: string;
  isActive: boolean;
  metadata?: Record<string, unknown>;
  webhookKeyConfigured?: boolean;
}

export interface FieldMappingRow {
  id: string;
  version: number;
  isActive: boolean;
  mapping: Record<string, unknown>;
  createdAt: string;
}

export interface TenantChannelConfigRow {
  id: string;
  tenantId: string;
  name: string;
  priority: number;
  isDefault: boolean;
  isEnabled: boolean;
  channelType?: { code: string };
}

export interface RoutingRuleRow {
  id: string;
  tenantId: string;
  name: string;
  eventType?: string;
  roleName?: string;
  channelTypeCodes?: string[];
  evalOrder: number;
  isActive: boolean;
  conditionsJsonlogic?: Record<string, unknown>;
}

export interface DbTemplateRow {
  id: string;
  tenantId: string;
  eventType: string;
  locale: string;
  subjectTemplate?: string;
  bodyTemplate: string;
  contentType: string;
  isActive: boolean;
  channelType?: { code: string };
  templateVersion?: number;
  template_version?: number;
}

export interface ChannelConfigurationRow {
  id: string;
  tenantId: string;
  app?: { id: string; sourceKey: string; displayName: string };
  channelType: "teams" | "whatsapp" | "telegram" | "smtp" | "webhook";
  configJson: Record<string, unknown>;
  isActive: boolean;
  createdAt: string;
}

export async function getNotifications(tenantId: string, userId?: string): Promise<Notification[]> {
  const { data } = await api.get<Notification[]>("/notifications", {
    params: { tenantId, ...(userId ? { userId } : {}) },
  });
  return Array.isArray(data) ? data : (data as { content?: Notification[] }).content ?? [];
}

export async function markNotificationRead(tenantId: string, userId: string, notificationId: string): Promise<void> {
  await api.post(`/notifications/${notificationId}/read`, null, {
    params: { tenantId, userId },
  });
}

export async function getRules(tenantId: string): Promise<NotificationRule[]> {
  const { data } = await api.get<NotificationRule[]>("/admin/rules", { params: { tenantId } });
  return Array.isArray(data) ? data : (data as { content?: NotificationRule[] }).content ?? [];
}

export async function createRule(tenantId: string, payload: RuleConfigInput): Promise<NotificationRule> {
  const { data } = await api.post<NotificationRule>("/admin/rules", payload, { params: { tenantId } });
  return data;
}

export async function updateRule(tenantId: string, ruleId: string, payload: RuleConfigInput): Promise<NotificationRule> {
  const { data } = await api.put<NotificationRule>(`/admin/rules/${ruleId}`, payload, { params: { tenantId } });
  return data;
}

export async function deleteRule(tenantId: string, ruleId: string): Promise<void> {
  await api.delete(`/admin/rules/${ruleId}`, { params: { tenantId } });
}

export async function getDeliveryLogs(
  tenantId: string,
  page = 0,
  size = 20
): Promise<PagedResponse<DeliveryLog>> {
  const { data } = await api.get<PagedResponse<DeliveryLog>>("/admin/delivery", { params: { tenantId, page, size } });
  return data;
}

export async function getDeliveryLogsByNotificationId(
  tenantId: string,
  notificationId: string
): Promise<DeliveryLog[]> {
  const { data } = await api.get<DeliveryLog[]>(`/admin/delivery/${notificationId}`, { params: { tenantId } });
  return Array.isArray(data) ? data : [];
}

export async function getAuditLogs(
  tenantId: string,
  page = 0,
  size = 20
): Promise<PagedResponse<AuditLog>> {
  const { data } = await api.get<PagedResponse<AuditLog>>("/admin/audit", { params: { tenantId, page, size } });
  return data;
}

export async function getIntegrationConfig(tenantId: string): Promise<IntegrationConfig> {
  const { data } = await api.get<IntegrationConfig>("/admin/config/integrations", { params: { tenantId } });
  return data;
}

export async function updateIntegrationConfig(tenantId: string, payload: IntegrationConfig): Promise<IntegrationConfig> {
  const { data } = await api.put<IntegrationConfig>("/admin/config/integrations", payload, { params: { tenantId } });
  return data;
}

export async function getWebhookSecurityConfig(tenantId: string): Promise<WebhookSecurityConfig> {
  const { data } = await api.get<WebhookSecurityConfig>("/admin/config/webhook-security", { params: { tenantId } });
  return data;
}

export async function updateWebhookSecurityConfig(
  tenantId: string,
  payload: WebhookSecurityConfig
): Promise<WebhookSecurityConfig> {
  const { data } = await api.put<WebhookSecurityConfig>("/admin/config/webhook-security", payload, {
    params: { tenantId },
  });
  return data;
}

export async function getHrmsMappingConfig(tenantId: string): Promise<HrmsMappingConfig> {
  const { data } = await api.get<HrmsMappingConfig>("/admin/config/hrms-mapping", { params: { tenantId } });
  return data;
}

export async function updateHrmsMappingConfig(tenantId: string, payload: HrmsMappingConfig): Promise<HrmsMappingConfig> {
  const { data } = await api.put<HrmsMappingConfig>("/admin/config/hrms-mapping", payload, { params: { tenantId } });
  return data;
}

export async function getTemplateConfig(tenantId: string): Promise<TemplateConfig> {
  const { data } = await api.get<TemplateConfig>("/admin/templates", { params: { tenantId } });
  return data;
}

export async function updateTemplateConfig(tenantId: string, payload: TemplateConfig): Promise<TemplateConfig> {
  const { data } = await api.put<TemplateConfig>("/admin/templates", payload, { params: { tenantId } });
  return data;
}

export async function getIntegrationSources(tenantId: string): Promise<IntegrationSourceRow[]> {
  const { data } = await api.get<IntegrationSourceRow[]>("/admin/integrations/sources", { params: { tenantId } });
  return Array.isArray(data) ? data : [];
}

export async function createIntegrationSource(
  tenantId: string,
  body: {
    sourceKey: string;
    displayName: string;
    isActive?: boolean;
    metadata?: Record<string, unknown>;
    webhookApiKey?: string;
  }
): Promise<IntegrationSourceRow> {
  const { data } = await api.post<IntegrationSourceRow>("/admin/integrations/sources", body, { params: { tenantId } });
  return data;
}

export async function updateIntegrationSource(
  tenantId: string,
  id: string,
  body: {
    displayName?: string;
    isActive?: boolean;
    metadata?: Record<string, unknown>;
    webhookApiKey?: string | null;
  }
): Promise<IntegrationSourceRow> {
  const { data } = await api.put<IntegrationSourceRow>(`/admin/integrations/sources/${id}`, body, { params: { tenantId } });
  return data;
}

export async function listFieldMappings(tenantId: string, integrationSourceId: string): Promise<FieldMappingRow[]> {
  const { data } = await api.get<FieldMappingRow[]>("/admin/integrations/field-mappings", {
    params: { tenantId, integrationSourceId },
  });
  return Array.isArray(data) ? data : [];
}

export async function createFieldMapping(
  tenantId: string,
  integrationSourceId: string,
  body: { version?: number; mapping: Record<string, unknown> }
): Promise<FieldMappingRow> {
  const { data } = await api.post<FieldMappingRow>("/admin/integrations/field-mappings", body, {
    params: { tenantId, integrationSourceId },
  });
  return data;
}

export async function getTenantChannelConfigs(tenantId: string): Promise<TenantChannelConfigRow[]> {
  const { data } = await api.get<TenantChannelConfigRow[]>("/admin/channel-configs", { params: { tenantId } });
  return Array.isArray(data) ? data : [];
}

export async function createTenantChannelConfig(
  tenantId: string,
  body: { channelTypeCode: string; name: string; config: Record<string, unknown>; priority?: number; isDefault?: boolean; isEnabled?: boolean }
): Promise<TenantChannelConfigRow> {
  const { data } = await api.post<TenantChannelConfigRow>("/admin/channel-configs", body, { params: { tenantId } });
  return data;
}

export async function updateTenantChannelConfig(
  tenantId: string,
  id: string,
  body: {
    name?: string;
    config?: Record<string, unknown>;
    priority?: number;
    isDefault?: boolean;
    isEnabled?: boolean;
  }
): Promise<TenantChannelConfigRow> {
  const { data } = await api.put<TenantChannelConfigRow>(`/admin/channel-configs/${id}`, body, { params: { tenantId } });
  return data;
}

export async function getRoutingRules(tenantId: string): Promise<RoutingRuleRow[]> {
  const { data } = await api.get<RoutingRuleRow[]>("/admin/routing-rules", { params: { tenantId } });
  return Array.isArray(data) ? data : [];
}

export async function createRoutingRule(tenantId: string, body: Record<string, unknown>): Promise<RoutingRuleRow> {
  const { data } = await api.post<RoutingRuleRow>("/admin/routing-rules", body, { params: { tenantId } });
  return data;
}

export async function updateRoutingRule(
  tenantId: string,
  ruleId: string,
  body: Record<string, unknown>
): Promise<RoutingRuleRow> {
  const { data } = await api.put<RoutingRuleRow>(`/admin/routing-rules/${ruleId}`, body, { params: { tenantId } });
  return data;
}

export async function deleteRoutingRule(tenantId: string, ruleId: string): Promise<void> {
  await api.delete(`/admin/routing-rules/${ruleId}`, { params: { tenantId } });
}

export async function getDbTemplates(tenantId: string): Promise<DbTemplateRow[]> {
  const { data } = await api.get<DbTemplateRow[] | { content?: DbTemplateRow[] }>("/admin/db-templates", {
    params: { tenantId },
  });
  return Array.isArray(data) ? data : (data as { content?: DbTemplateRow[] }).content ?? [];
}

export async function createDbTemplate(tenantId: string, body: Record<string, unknown>): Promise<DbTemplateRow> {
  const { data } = await api.post<DbTemplateRow>("/admin/db-templates", body, { params: { tenantId } });
  return data;
}

export async function updateDbTemplate(
  tenantId: string,
  id: string,
  body: Record<string, unknown>
): Promise<DbTemplateRow> {
  const { data } = await api.put<DbTemplateRow>(`/admin/db-templates/${id}`, body, { params: { tenantId } });
  return data;
}

export async function deleteDbTemplate(tenantId: string, id: string): Promise<void> {
  await api.delete(`/admin/db-templates/${id}`, { params: { tenantId } });
}

export async function listChannelConfigurations(tenantId: string): Promise<ChannelConfigurationRow[]> {
  const { data } = await api.get<ChannelConfigurationRow[]>("/admin/channel-configurations", { params: { tenantId } });
  return Array.isArray(data) ? data : [];
}

export async function createChannelConfiguration(
  tenantId: string,
  body: { appId: string; channelType: string; configJson: Record<string, unknown>; isActive?: boolean }
): Promise<ChannelConfigurationRow> {
  const { data } = await api.post<ChannelConfigurationRow>("/admin/channel-configurations", body, { params: { tenantId } });
  return data;
}

export async function updateChannelConfiguration(
  tenantId: string,
  id: string,
  body: { appId?: string; channelType?: string; configJson?: Record<string, unknown>; isActive?: boolean }
): Promise<ChannelConfigurationRow> {
  const { data } = await api.put<ChannelConfigurationRow>(`/admin/channel-configurations/${id}`, body, { params: { tenantId } });
  return data;
}

export async function deleteChannelConfiguration(tenantId: string, id: string): Promise<void> {
  await api.delete(`/admin/channel-configurations/${id}`, { params: { tenantId } });
}

export async function testChannelConfiguration(tenantId: string, id: string): Promise<{ ok: boolean; message: string }> {
  const { data } = await api.post<{ ok: boolean; message: string }>(
    `/admin/channel-configurations/${id}/test`,
    null,
    { params: { tenantId } }
  );
  return data;
}
