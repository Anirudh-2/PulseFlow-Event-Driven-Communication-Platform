import { PlatformAdminPanel } from "../configuration/PlatformAdminPanel";
import { useTenantContext } from "../../app/TenantContext";

export function ApplicationsPage() {
  const { tenantId } = useTenantContext();
  return <PlatformAdminPanel tenantId={tenantId} />;
}
