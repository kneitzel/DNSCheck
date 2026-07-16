import { useCallback, useEffect, useRef, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import {
  AuthService,
  OpenAPI,
  ReconciliationService,
  ZoneViewService,
  ZonesService,
  AdminService,
  type CreateRecordRequest,
  type CreateZoneRequest,
  type DeletionMarkerRequest,
  type LoginRequest,
  type ModifyRecordRequest,
  type RecordRef,
  type ReconciliationStatusResponse,
  type UpdateZoneRequest,
  type ZoneCatalogEntry,
  type ZoneViewRecord,
  type ZoneViewResponse,
} from "./generated";
import { ApiError } from "./generated/core/ApiError";
import {
  canManageZone,
  canTransferZone,
  catalogRef,
  defaultOwnerFolder,
  hasSupplementalWrite,
  type SelectedZoneRef,
} from "./zoneCatalog";
import { AppShell } from "./components/AppShell";
import { LoginPanel } from "./components/LoginPanel";
import { ReconcileDashboard } from "./components/ReconcileDashboard";
import { MissingKeysPanel } from "./components/MissingKeysPanel";
import { ZoneCatalogPanel } from "./components/ZoneCatalogPanel";
import { ZoneViewPanel } from "./components/ZoneViewPanel";
import { ZoneViewErrorBanner } from "./components/ZoneViewErrorBanner";
import { formatApiError } from "./apiErrorMessage";
import { AccountPage } from "./pages/AccountPage";
import { AccountKeysPage } from "./pages/AccountKeysPage";
import { GroupDetailPage } from "./pages/GroupDetailPage";
import { GroupsListPage } from "./pages/GroupsListPage";
import { OperatorDetailPage } from "./pages/OperatorDetailPage";
import { OperatorsListPage } from "./pages/OperatorsListPage";

OpenAPI.WITH_CREDENTIALS = true;

export default function App() {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin");
  const [mfaCode, setMfaCode] = useState("");
  const [loggedInUser, setLoggedInUser] = useState<string | null>(null);
  const [status, setStatus] = useState<ReconciliationStatusResponse | null>(null);
  const [zones, setZones] = useState<ZoneCatalogEntry[]>([]);
  const [selectedZone, setSelectedZone] = useState<SelectedZoneRef | null>(null);
  const [zoneView, setZoneView] = useState<ZoneViewResponse | null>(null);
  const [zoneViewError, setZoneViewError] = useState<string | null>(null);
  const zoneViewRequestId = useRef(0);
  const [recordName, setRecordName] = useState("");
  const [recordType, setRecordType] = useState("A");
  const [recordRdata, setRecordRdata] = useState("");
  const [recordTtl, setRecordTtl] = useState("");
  const [recordEnforce, setRecordEnforce] = useState(true);
  const [modifyRecord, setModifyRecord] = useState<ZoneViewRecord | null>(null);
  const [modifyRdata, setModifyRdata] = useState("");
  const [modifyTtl, setModifyTtl] = useState("");
  const [createZoneOpen, setCreateZoneOpen] = useState(false);
  const [newZoneOwner, setNewZoneOwner] = useState("");
  const [newZoneName, setNewZoneName] = useState("");
  const [newZoneNameserver, setNewZoneNameserver] = useState("");
  const [newZoneKeyFile, setNewZoneKeyFile] = useState("");
  const [createZoneError, setCreateZoneError] = useState<string | null>(null);
  const [editZoneOpen, setEditZoneOpen] = useState(false);
  const [deleteZoneOpen, setDeleteZoneOpen] = useState(false);
  const [transferZoneOpen, setTransferZoneOpen] = useState(false);
  const [editingZone, setEditingZone] = useState<ZoneCatalogEntry | null>(null);
  const [deletingZone, setDeletingZone] = useState<ZoneCatalogEntry | null>(null);
  const [transferringZone, setTransferringZone] = useState<ZoneCatalogEntry | null>(null);
  const [editZoneNameserver, setEditZoneNameserver] = useState("");
  const [editZoneKeyFile, setEditZoneKeyFile] = useState("");
  const [transferTargetOwner, setTransferTargetOwner] = useState("");
  const [editZoneError, setEditZoneError] = useState<string | null>(null);
  const [deleteZoneError, setDeleteZoneError] = useState<string | null>(null);
  const [transferZoneError, setTransferZoneError] = useState<string | null>(null);
  const [canManageCatalog, setCanManageCatalog] = useState(false);
  const [isAdmin, setIsAdmin] = useState(false);
  const [canWriteOnZone, setCanWriteOnZone] = useState(false);
  const [loading, setLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

  const loadDashboard = useCallback(async () => {
    try {
      const [statusResponse, zonesResponse] = await Promise.all([
        ReconciliationService.getReconciliationStatus(),
        ZonesService.listZones(),
      ]);
      setStatus(statusResponse);
      setZones(zonesResponse.entries);
    } catch {
      setStatus(null);
      setZones([]);
      setLoggedInUser(null);
    }
  }, []);

  const loadCapabilities = useCallback(async () => {
    try {
      await AdminService.listOperators();
      setIsAdmin(true);
      setCanManageCatalog(true);
    } catch {
      setIsAdmin(false);
      try {
        await ZonesService.createZone({
          owner: loggedInUser ? defaultOwnerFolder(loggedInUser) : "operator.u",
          zone: "",
          nameserver: "",
          keyFile: "",
        });
      } catch (error) {
        setCanManageCatalog(error instanceof ApiError && error.status === 400);
      }
    }
  }, [loggedInUser]);

  const loadZoneView = useCallback(
    async (entry: ZoneCatalogEntry) => {
      const requestId = ++zoneViewRequestId.current;
      const ref = { owner: entry.ownerFolder, zoneName: entry.zone };
      setSelectedZone(ref);
      setZoneView(null);
      setZoneViewError(null);
      setCanWriteOnZone(false);
      setLoading(true);
      try {
        const response = await ZoneViewService.getZoneView(entry.ownerFolder, entry.zone);
        if (requestId !== zoneViewRequestId.current) {
          return;
        }
        setZoneView(response);
        setZoneViewError(null);
        let supplementalWrite = false;
        try {
          const rights = await AuthService.getMyRights();
          supplementalWrite = hasSupplementalWrite(
            entry.ownerFolder,
            entry.zone,
            rights.zoneRights,
          );
        } catch {
          supplementalWrite = false;
        }
        setCanWriteOnZone(
          loggedInUser
            ? canManageZone(entry.ownerFolder, loggedInUser, isAdmin) || supplementalWrite
            : false,
        );
      } catch (error) {
        if (requestId !== zoneViewRequestId.current) {
          return;
        }
        setZoneView(null);
        setZoneViewError(formatApiError(error));
        setCanWriteOnZone(false);
      } finally {
        if (requestId === zoneViewRequestId.current) {
          setLoading(false);
        }
      }
    },
    [isAdmin, loggedInUser],
  );

  const recordRefFor = (record: ZoneViewRecord): RecordRef => ({
    name: record.name,
    type: record.type,
    rdata: record.expectedRdata ?? record.rdata ?? "",
  });

  const applyZoneView = (response: ZoneViewResponse) => {
    setZoneView(response);
    setZoneViewError(null);
    setCanWriteOnZone(true);
  };

  const createRecord = async () => {
    if (!selectedZone) {
      return;
    }
    setLoading(true);
    try {
      const request: CreateRecordRequest = {
        name: recordName,
        type: recordType,
        rdata: recordRdata,
        enforce: recordEnforce,
        ttl: recordTtl ? Number(recordTtl) : undefined,
      };
      const response = await ZonesService.createZoneRecord(
        selectedZone.owner,
        selectedZone.zoneName,
        request,
      );
      applyZoneView(response);
      setRecordName("");
      setRecordRdata("");
      setRecordTtl("");
    } finally {
      setLoading(false);
    }
  };

  const promoteRecord = async (record: ZoneViewRecord) => {
    if (!selectedZone) {
      return;
    }
    setLoading(true);
    try {
      const response = await ZonesService.promoteZoneRecord(selectedZone.owner, selectedZone.zoneName, {
        name: record.name,
        type: record.type,
        rdata: record.rdata ?? "",
      });
      applyZoneView(response);
    } finally {
      setLoading(false);
    }
  };

  const demoteRecord = async (record: ZoneViewRecord) => {
    if (!selectedZone) {
      return;
    }
    setLoading(true);
    try {
      const response = await ZonesService.demoteZoneRecord(
        selectedZone.owner,
        selectedZone.zoneName,
        recordRefFor(record),
      );
      applyZoneView(response);
    } finally {
      setLoading(false);
    }
  };

  const deleteRecord = async (record: ZoneViewRecord) => {
    if (!selectedZone) {
      return;
    }
    setLoading(true);
    try {
      const response = await ZonesService.deleteZoneRecord(
        selectedZone.owner,
        selectedZone.zoneName,
        recordRefFor(record),
      );
      applyZoneView(response);
    } finally {
      setLoading(false);
    }
  };

  const markForDeletion = async (record: ZoneViewRecord, rdataPattern: string) => {
    if (!selectedZone) {
      return;
    }
    setLoading(true);
    try {
      const request: DeletionMarkerRequest = {
        name: record.name,
        type: record.type,
        rdataPattern: rdataPattern || undefined,
      };
      const response = await ZonesService.createDeletionMarker(
        selectedZone.owner,
        selectedZone.zoneName,
        request,
      );
      applyZoneView(response);
    } finally {
      setLoading(false);
    }
  };

  const removeDeletionMarker = async (record: ZoneViewRecord, rdataPattern: string) => {
    if (!selectedZone) {
      return;
    }
    setLoading(true);
    try {
      const request: DeletionMarkerRequest = {
        name: record.name,
        type: record.type,
        rdataPattern: rdataPattern || undefined,
      };
      const response = await ZonesService.deleteDeletionMarker(
        selectedZone.owner,
        selectedZone.zoneName,
        request,
      );
      applyZoneView(response);
    } finally {
      setLoading(false);
    }
  };

  const openModifyDialog = (record: ZoneViewRecord) => {
    setModifyRecord(record);
    if (record.status === "ad_hoc") {
      setModifyRdata(record.rdata ?? "");
      setModifyTtl("");
    } else {
      setModifyRdata(record.expectedRdata ?? record.rdata ?? "");
      setModifyTtl(record.expectedTtl?.toString() ?? record.ttl?.toString() ?? "");
    }
  };

  const submitModify = async () => {
    if (!selectedZone || !modifyRecord) {
      return;
    }
    setLoading(true);
    try {
      const request: ModifyRecordRequest = {
        ...recordRefFor(modifyRecord),
        newRdata: modifyRdata || undefined,
        newTtl: modifyTtl ? Number(modifyTtl) : undefined,
      };
      const response = await ZonesService.modifyZoneRecord(
        selectedZone.owner,
        selectedZone.zoneName,
        request,
      );
      applyZoneView(response);
      setModifyRecord(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (loggedInUser) {
      void loadDashboard();
      void loadCapabilities();
    }
  }, [loggedInUser, loadDashboard, loadCapabilities]);

  useEffect(() => {
    if (!selectedZone || !loggedInUser) {
      return;
    }
    let cancelled = false;
    void (async () => {
      let supplementalWrite = false;
      try {
        const rights = await AuthService.getMyRights();
        supplementalWrite = hasSupplementalWrite(
          selectedZone.owner,
          selectedZone.zoneName,
          rights.zoneRights,
        );
      } catch {
        supplementalWrite = false;
      }
      if (!cancelled) {
        setCanWriteOnZone(
          canManageZone(selectedZone.owner, loggedInUser, isAdmin) || supplementalWrite,
        );
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [isAdmin, selectedZone, loggedInUser]);

  const login = async () => {
    setAuthError(null);
    setLoading(true);
    try {
      const request: LoginRequest = { username, password, mfaCode: mfaCode || undefined };
      const response = await AuthService.login(request);
      setLoggedInUser(response.username);
      setNewZoneOwner(defaultOwnerFolder(response.username));
      setMfaCode("");
    } catch (error) {
      setLoggedInUser(null);
      setAuthError(formatApiError(error));
    } finally {
      setLoading(false);
    }
  };

  const logout = async () => {
    setLoading(true);
    try {
      await AuthService.logout();
    } finally {
      setLoggedInUser(null);
      setStatus(null);
      setZones([]);
      setSelectedZone(null);
      setZoneView(null);
      setZoneViewError(null);
      setIsAdmin(false);
      setCanManageCatalog(false);
      setCanWriteOnZone(false);
      setLoading(false);
    }
  };

  const triggerReconcile = async () => {
    setLoading(true);
    try {
      const response = await ReconciliationService.triggerReconciliation(true);
      setStatus(response);
    } finally {
      setLoading(false);
    }
  };

  const reconcileSelectedZone = async (dryRun: boolean) => {
    if (!selectedZone) {
      return;
    }
    setLoading(true);
    try {
      const response = await ZonesService.triggerZoneReconciliation(
        selectedZone.owner,
        selectedZone.zoneName,
        dryRun,
      );
      setStatus(response);
      const entry = zones.find(
        (zone) => zone.ownerFolder === selectedZone.owner && zone.zone === selectedZone.zoneName,
      );
      if (entry) {
        await loadZoneView(entry);
      }
    } finally {
      setLoading(false);
    }
  };

  const createZone = async () => {
    setCreateZoneError(null);
    setLoading(true);
    try {
      const request: CreateZoneRequest = {
        owner: newZoneOwner,
        zone: newZoneName,
        nameserver: newZoneNameserver,
        keyFile: newZoneKeyFile,
      };
      await ZonesService.createZone(request);
      setCanManageCatalog(true);
      setCreateZoneOpen(false);
      setNewZoneOwner(loggedInUser ? defaultOwnerFolder(loggedInUser) : "");
      setNewZoneName("");
      setNewZoneNameserver("");
      setNewZoneKeyFile("");
      await loadDashboard();
    } catch {
      setCreateZoneError("Could not create zone. Check permissions and that the zone does not already exist.");
    } finally {
      setLoading(false);
    }
  };

  const openEditZone = (zone: ZoneCatalogEntry) => {
    setEditingZone(zone);
    setEditZoneNameserver(zone.nameserver ?? "");
    setEditZoneKeyFile(`${zone.ownerFolder}/${zone.zone}.key`);
    setEditZoneError(null);
    setEditZoneOpen(true);
  };

  const updateZone = async () => {
    if (!editingZone) {
      return;
    }
    setEditZoneError(null);
    setLoading(true);
    try {
      const request: UpdateZoneRequest = {
        nameserver: editZoneNameserver,
        keyFile: editZoneKeyFile,
      };
      await ZonesService.updateZone(editingZone.ownerFolder, editingZone.zone, request);
      setEditZoneOpen(false);
      setEditingZone(null);
      if (selectedZone?.owner === editingZone.ownerFolder && selectedZone.zoneName === editingZone.zone) {
        setSelectedZone(null);
        setZoneView(null);
        setZoneViewError(null);
      }
      await loadDashboard();
    } catch {
      setEditZoneError("Could not update zone. Check permissions and field values.");
    } finally {
      setLoading(false);
    }
  };

  const openDeleteZone = (zone: ZoneCatalogEntry) => {
    setDeletingZone(zone);
    setDeleteZoneError(null);
    setDeleteZoneOpen(true);
  };

  const openTransferZone = (zone: ZoneCatalogEntry) => {
    setTransferringZone(zone);
    setTransferTargetOwner("");
    setTransferZoneError(null);
    setTransferZoneOpen(true);
  };

  const transferZoneDefinition = async () => {
    if (!transferringZone) {
      return;
    }
    setTransferZoneError(null);
    setLoading(true);
    try {
      await ZonesService.transferZone(transferringZone.ownerFolder, transferringZone.zone, {
        targetOwner: transferTargetOwner,
      });
      setTransferZoneOpen(false);
      if (
        selectedZone?.owner === transferringZone.ownerFolder &&
        selectedZone.zoneName === transferringZone.zone
      ) {
        setSelectedZone(null);
        setZoneView(null);
        setZoneViewError(null);
      }
      setTransferringZone(null);
      await loadDashboard();
    } catch {
      setTransferZoneError("Could not transfer zone. Check permissions and that the target does not already exist.");
    } finally {
      setLoading(false);
    }
  };

  const deleteZoneDefinition = async () => {
    if (!deletingZone) {
      return;
    }
    setDeleteZoneError(null);
    setLoading(true);
    try {
      await ZonesService.deleteZone(deletingZone.ownerFolder, deletingZone.zone);
      setDeleteZoneOpen(false);
      if (selectedZone?.owner === deletingZone.ownerFolder && selectedZone.zoneName === deletingZone.zone) {
        setSelectedZone(null);
        setZoneView(null);
        setZoneViewError(null);
      }
      setDeletingZone(null);
      await loadDashboard();
    } catch {
      setDeleteZoneError("Could not delete zone. Check permissions.");
    } finally {
      setLoading(false);
    }
  };

  if (!loggedInUser) {
    return (
      <LoginPanel
        username={username}
        password={password}
        mfaCode={mfaCode}
        authError={authError}
        loading={loading}
        onUsernameChange={setUsername}
        onPasswordChange={setPassword}
        onMfaCodeChange={setMfaCode}
        onLogin={() => void login()}
      />
    );
  }

  return (
    <AppShell
      loggedInUser={loggedInUser}
      isAdmin={isAdmin}
      loading={loading}
      onRefresh={() => void loadDashboard()}
      onLogout={() => void logout()}
    >
      <Routes>
        <Route
          path="/"
          element={
            <>
              <ZoneCatalogPanel
                zones={zones}
                loading={loading}
                canManageCatalog={canManageCatalog}
                isAdmin={isAdmin}
                personalOwnerFolder={loggedInUser ? defaultOwnerFolder(loggedInUser) : "operator.u"}
                canTransferZone={(zone) =>
                  loggedInUser ? canTransferZone(zone.ownerFolder, loggedInUser, isAdmin) : false
                }
                createZoneOpen={createZoneOpen}
                editZoneOpen={editZoneOpen}
                deleteZoneOpen={deleteZoneOpen}
                transferZoneOpen={transferZoneOpen}
                editingZone={editingZone}
                deletingZone={deletingZone}
                transferringZone={transferringZone}
                newZoneOwner={newZoneOwner}
                newZoneName={newZoneName}
                newZoneNameserver={newZoneNameserver}
                newZoneKeyFile={newZoneKeyFile}
                editZoneNameserver={editZoneNameserver}
                editZoneKeyFile={editZoneKeyFile}
                transferTargetOwner={transferTargetOwner}
                createZoneError={createZoneError}
                editZoneError={editZoneError}
                deleteZoneError={deleteZoneError}
                transferZoneError={transferZoneError}
                onOpenCreateZone={() => {
                  if (loggedInUser) {
                    setNewZoneOwner(defaultOwnerFolder(loggedInUser));
                  }
                  setCreateZoneOpen(true);
                }}
                onCloseCreateZone={() => setCreateZoneOpen(false)}
                onOpenEditZone={openEditZone}
                onCloseEditZone={() => setEditZoneOpen(false)}
                onOpenDeleteZone={openDeleteZone}
                onCloseDeleteZone={() => setDeleteZoneOpen(false)}
                onOpenTransferZone={openTransferZone}
                onCloseTransferZone={() => setTransferZoneOpen(false)}
                onNewZoneOwnerChange={setNewZoneOwner}
                onNewZoneNameChange={setNewZoneName}
                onNewZoneNameserverChange={setNewZoneNameserver}
                onNewZoneKeyFileChange={setNewZoneKeyFile}
                onEditZoneNameserverChange={setEditZoneNameserver}
                onEditZoneKeyFileChange={setEditZoneKeyFile}
                onTransferTargetOwnerChange={setTransferTargetOwner}
                onCreateZone={() => void createZone()}
                onUpdateZone={() => void updateZone()}
                onDeleteZone={() => void deleteZoneDefinition()}
                onTransferZone={() => void transferZoneDefinition()}
                onSelectZone={(zone) => void loadZoneView(zone)}
              />

              {selectedZone && zoneViewError && (
                <ZoneViewErrorBanner
                  selectedZone={catalogRef({
                    ownerFolder: selectedZone.owner,
                    zone: selectedZone.zoneName,
                  })}
                  error={zoneViewError}
                  loading={loading}
                  onRetry={() => {
                    const entry = zones.find(
                      (zone) =>
                        zone.ownerFolder === selectedZone.owner && zone.zone === selectedZone.zoneName,
                    );
                    if (entry) {
                      void loadZoneView(entry);
                    }
                  }}
                />
              )}

              {selectedZone && zoneView && (
                <ZoneViewPanel
                  selectedZone={catalogRef({
                    ownerFolder: selectedZone.owner,
                    zone: selectedZone.zoneName,
                  })}
                  zoneView={zoneView}
                  loading={loading}
                  recordName={recordName}
                  recordType={recordType}
                  recordRdata={recordRdata}
                  recordTtl={recordTtl}
                  recordEnforce={recordEnforce}
                  modifyRecord={modifyRecord}
                  modifyRdata={modifyRdata}
                  modifyTtl={modifyTtl}
                  onRefresh={() => {
                    const entry = zones.find(
                      (zone) =>
                        zone.ownerFolder === selectedZone.owner && zone.zone === selectedZone.zoneName,
                    );
                    if (entry) {
                      void loadZoneView(entry);
                    }
                  }}
                  showReconcileActions={isAdmin || canWriteOnZone}
                  canWrite={isAdmin || canWriteOnZone}
                  onDryRunReconcile={() => void reconcileSelectedZone(true)}
                  onLiveReconcile={() => void reconcileSelectedZone(false)}
                  onRecordNameChange={setRecordName}
                  onRecordTypeChange={setRecordType}
                  onRecordRdataChange={setRecordRdata}
                  onRecordTtlChange={setRecordTtl}
                  onRecordEnforceChange={setRecordEnforce}
                  onCreateRecord={() => void createRecord()}
                  onPromote={(record) => void promoteRecord(record)}
                  onDemote={(record) => void demoteRecord(record)}
                  onDelete={(record) => void deleteRecord(record)}
                  onMarkForDeletion={(record, pattern) => void markForDeletion(record, pattern)}
                  onRemoveMarker={(record, pattern) => void removeDeletionMarker(record, pattern)}
                  onModifyOpen={openModifyDialog}
                  onModifyClose={() => setModifyRecord(null)}
                  onModifyRdataChange={setModifyRdata}
                  onModifyTtlChange={setModifyTtl}
                  onSubmitModify={() => void submitModify()}
                />
              )}

              <MissingKeysPanel />

              {isAdmin && (
                <ReconcileDashboard
                  status={status}
                  loading={loading}
                  onTriggerReconcile={() => void triggerReconcile()}
                />
              )}
            </>
          }
        />
        <Route path="/account" element={<AccountPage />} />
        <Route path="/account/keys" element={<AccountKeysPage />} />
        <Route
          path="/admin/operators"
          element={isAdmin ? <OperatorsListPage /> : <Navigate to="/" replace />}
        />
        <Route
          path="/admin/operators/:operatorId"
          element={isAdmin ? <OperatorDetailPage /> : <Navigate to="/" replace />}
        />
        <Route
          path="/admin/groups"
          element={isAdmin ? <GroupsListPage /> : <Navigate to="/" replace />}
        />
        <Route path="/admin/groups/:groupId" element={<GroupDetailPage />} />
      </Routes>
    </AppShell>
  );
}
