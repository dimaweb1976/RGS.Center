"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CurrentUser, getCurrentUser, logout } from "@/lib/auth";
import styles from "./devices.module.css";

type ConnectionStatus = "ONLINE" | "OFFLINE" | "DISABLED";

type Device = {
  code: string;
  name: string;
  address: string;
  enabled: boolean;
  connectionStatus: ConnectionStatus;
  lastCheck: string | null;
};

type DevicesResponse = {
  generatedAt: string;
  devices: Device[];
  nicole: NicoleStatus;
  shp: ShpStatus;
};

type TechnicalStatus = "READY" | "ALERT" | "NO_DATA" | "DISABLED";

type NicoleSnapshot = {
  resetCause: number;
  ethernetUp: boolean;
  dhcpLeased: boolean;
  cryoControllerCommunication: boolean;
  cryocoolerStarted: boolean;
  advancedMode: boolean;
  currentNitrogenLevel: number;
  currentNitrogenPressure: number;
  lowerLevelVoltage: number;
  upperLevelVoltage: number;
  nitrogenLevelFrequency: number;
  controllerTime: string | null;
  coolerErrorCode: number;
  warningFlags: number;
  receivedAt: string;
};

type NicoleStatus = {
  connectionStatus: ConnectionStatus;
  technicalStatus: TechnicalStatus;
  warningText: string;
  snapshot: NicoleSnapshot | null;
};

type ShpStatus = {
  ambientTemperatureCelsius: number | null;
  humidityPercent: number | null;
  atmosphericPressureHpa: number | null;
  leakageCurrent: number | null;
  chargeOutputVoltage: number | null;
  technicalStatus: Exclude<TechnicalStatus, "DISABLED">;
};

const statusLabels: Record<ConnectionStatus, string> = {
  ONLINE: "В сети",
  OFFLINE: "Нет связи",
  DISABLED: "Отключено"
};

const technicalStatusLabels: Record<TechnicalStatus, string> = {
  READY: "Готово",
  ALERT: "Авария",
  NO_DATA: "Нет данных",
  DISABLED: "Отключено"
};

function statusClass(status: ConnectionStatus | TechnicalStatus): string {
  if (status === "ONLINE" || status === "READY") return styles.online;
  if (status === "OFFLINE" || status === "ALERT") return styles.offline;
  return styles.disabled;
}

function StatusPill({ status, label }: { status: ConnectionStatus | TechnicalStatus; label: string }) {
  return (
    <span className={`${styles.status} ${statusClass(status)}`}>
      <span />{label}
    </span>
  );
}

function roleLabel(role: string): string {
  const labels: Record<string, string> = {
    ADMIN: "Администратор",
    OPERATOR: "Оператор",
    ENGINEER: "Сервисный инженер"
  };
  return labels[role.replace(/^ROLE_/, "")] ?? role;
}

function formatDate(value: string | null): string {
  if (!value) return "—";

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  return new Intl.DateTimeFormat("ru-RU", {
    dateStyle: "short",
    timeStyle: "medium"
  }).format(date);
}

function formatMetric(value: number | null, unit = ""): string {
  if (value === null) return "—";
  const formatted = new Intl.NumberFormat("ru-RU", {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3
  }).format(value);
  return unit ? `${formatted} ${unit}` : formatted;
}

export default function DevicesPage() {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [data, setData] = useState<DevicesResponse | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [loggingOut, setLoggingOut] = useState(false);

  const returnToLogin = useCallback(() => {
    router.replace("/login");
  }, [router]);

  const loadDevices = useCallback(async () => {
    try {
      const response = await fetch("/api/v1/devices", {
        credentials: "include",
        cache: "no-store"
      });

      if (response.status === 401) {
        returnToLogin();
        return;
      }
      if (!response.ok) {
        throw new Error(`Ошибка HTTP ${response.status}`);
      }

      setData((await response.json()) as DevicesResponse);
      setError("");
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Не удалось получить состояние устройств"
      );
    } finally {
      setLoading(false);
    }
  }, [returnToLogin]);

  useEffect(() => {
    let active = true;

    getCurrentUser()
      .then((currentUser) => {
        if (!active) return;
        if (!currentUser) {
          returnToLogin();
          return;
        }
        setUser(currentUser);
        void loadDevices();
      })
      .catch((requestError) => {
        if (!active) return;
        setError(
          requestError instanceof Error
            ? requestError.message
            : "Не удалось проверить сессию"
        );
        setLoading(false);
      });

    const timer = window.setInterval(() => {
      if (active) void loadDevices();
    }, 10_000);

    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [loadDevices, returnToLogin]);

  async function handleLogout() {
    setLoggingOut(true);
    setError("");
    try {
      await logout();
      returnToLogin();
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Не удалось завершить сессию"
      );
      setLoggingOut(false);
    }
  }

  const online = data?.devices.filter((device) => device.connectionStatus === "ONLINE").length ?? 0;
  const offline = data?.devices.filter((device) => device.connectionStatus === "OFFLINE").length ?? 0;
  const disabled = data?.devices.filter((device) => device.connectionStatus === "DISABLED").length ?? 0;

  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <div>
          <span className={styles.brand}>RGSCenter</span>
          <span className={styles.section}>Состояние оборудования</span>
        </div>

        <div className={styles.account}>
          {user && (
            <div className={styles.userInfo}>
              <strong>{user.username}</strong>
              <span>{roleLabel(user.role)}</span>
            </div>
          )}
          <button type="button" onClick={handleLogout} disabled={loggingOut}>
            {loggingOut ? "Выход…" : "Выйти"}
          </button>
        </div>
      </header>

      <main className={styles.content}>
        <div className={styles.titleRow}>
          <div>
            <h1>Статусы устройств</h1>
            <p>Автоматическое обновление каждые 10 секунд</p>
          </div>
          <button
            className={styles.refreshButton}
            type="button"
            onClick={() => void loadDevices()}
            disabled={loading}
          >
            {loading ? "Обновление…" : "Обновить"}
          </button>
        </div>

        {user?.multipleConnectionsWarning && (
          <div className={styles.warning} role="alert">
            Обнаружено несколько активных подключений: {user.connected}.
          </div>
        )}

        {error && (
          <div className={styles.error} role="alert">
            {error}. Следующая попытка будет выполнена автоматически.
          </div>
        )}

        <section className={styles.summary} aria-label="Сводка статусов">
          <div><span className={styles.onlineDot} />В сети <strong>{online}</strong></div>
          <div><span className={styles.offlineDot} />Нет связи <strong>{offline}</strong></div>
          <div><span className={styles.disabledDot} />Отключено <strong>{disabled}</strong></div>
        </section>

        <section className={styles.tableCard} aria-labelledby="devices-table-title">
          <div className={styles.tableHeading}>
            <h2 id="devices-table-title">Подключённое оборудование</h2>
            <span>Последнее обновление: {data ? formatDate(data.generatedAt) : "—"}</span>
          </div>

          <div className={styles.tableScroll}>
            <table>
              <thead>
                <tr>
                  <th>Устройство</th>
                  <th>IP-адрес / порт</th>
                  <th>Состояние</th>
                  <th>Последняя проверка</th>
                </tr>
              </thead>
              <tbody>
                {data?.devices.map((device) => (
                  <tr key={device.code}>
                    <td className={styles.deviceName}>{device.name}</td>
                    <td className={styles.address}>{device.address || "—"}</td>
                    <td>
                      <StatusPill
                        status={device.connectionStatus}
                        label={statusLabels[device.connectionStatus]}
                      />
                    </td>
                    <td>{formatDate(device.lastCheck)}</td>
                  </tr>
                ))}
                {!data && (
                  <tr>
                    <td className={styles.loadingCell} colSpan={4}>Загрузка статусов…</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </section>

        {data && (
          <section className={styles.detailSection} aria-labelledby="nicole-state-title">
            <div className={styles.detailHeading}>
              <div>
                <h2 id="nicole-state-title">Состояние NICOLE</h2>
                <p>Последний корректно разобранный протокольный PONG</p>
              </div>
              {data.nicole.snapshot?.receivedAt && (
                <span>Получено: {formatDate(data.nicole.snapshot.receivedAt)}</span>
              )}
            </div>

            <div className={styles.detailCard}>
              <div className={styles.tableScroll}>
                <table className={styles.nicoleTable}>
                  <thead>
                    <tr>
                      <th>Контроллер</th>
                      <th>Криоохладитель</th>
                      <th>Азот</th>
                      <th>Напряжения уровней</th>
                      <th>Время контроллера</th>
                      <th>Ошибка охладителя</th>
                      <th>Предупреждения</th>
                      <th>Состояние связи</th>
                      <th>Техническое состояние</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>
                        {data.nicole.snapshot ? (
                          <div className={styles.detailsStack}>
                            <span>Ethernet: {data.nicole.snapshot.ethernetUp ? "Up" : "Down"}</span>
                            <span>DHCP: {data.nicole.snapshot.dhcpLeased ? "Lease получен" : "Static / no lease"}</span>
                            <span>Cryo link: {data.nicole.snapshot.cryoControllerCommunication ? "OK" : "Нет связи"}</span>
                            <span>Reset cause: {data.nicole.snapshot.resetCause}</span>
                          </div>
                        ) : "—"}
                      </td>
                      <td>
                        {data.nicole.snapshot ? (
                          <div className={styles.detailsStack}>
                            <span>{data.nicole.snapshot.cryocoolerStarted ? "Запущен" : "Остановлен"}</span>
                            <span>{data.nicole.snapshot.advancedMode ? "Расширенный режим" : "Нормальный режим"}</span>
                          </div>
                        ) : "—"}
                      </td>
                      <td>
                        {data.nicole.snapshot ? (
                          <div className={styles.detailsStack}>
                            <span>Уровень: {data.nicole.snapshot.currentNitrogenLevel}</span>
                            <span>Давление: {data.nicole.snapshot.currentNitrogenPressure}</span>
                            <span>Частота: {data.nicole.snapshot.nitrogenLevelFrequency}</span>
                          </div>
                        ) : "—"}
                      </td>
                      <td>
                        {data.nicole.snapshot ? (
                          <div className={styles.detailsStack}>
                            <span>Нижнее: {data.nicole.snapshot.lowerLevelVoltage}</span>
                            <span>Верхнее: {data.nicole.snapshot.upperLevelVoltage}</span>
                          </div>
                        ) : "—"}
                      </td>
                      <td>{data.nicole.snapshot ? formatDate(data.nicole.snapshot.controllerTime) : "—"}</td>
                      <td>{data.nicole.snapshot?.coolerErrorCode ?? "—"}</td>
                      <td>{data.nicole.warningText?.trim() || "Нет"}</td>
                      <td>
                        <StatusPill
                          status={data.nicole.connectionStatus}
                          label={statusLabels[data.nicole.connectionStatus]}
                        />
                      </td>
                      <td>
                        <StatusPill
                          status={data.nicole.technicalStatus}
                          label={technicalStatusLabels[data.nicole.technicalStatus]}
                        />
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        )}

        {data && (
          <section className={styles.detailSection} aria-labelledby="shp-measurements-title">
            <div className={styles.detailHeading}>
              <div>
                <h2 id="shp-measurements-title">Измерения SHP</h2>
                <p>Последний полный набор значений предусилителя</p>
              </div>
            </div>

            <div className={styles.detailCard}>
              <div className={styles.tableScroll}>
                <table className={styles.shpTable}>
                  <thead>
                    <tr>
                      <th>Температура</th>
                      <th>Влажность</th>
                      <th>Давление</th>
                      <th>Ток утечки</th>
                      <th>Выходное напряжение</th>
                      <th>Состояние</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td className={styles.measurement}>{formatMetric(data.shp.ambientTemperatureCelsius, "°C")}</td>
                      <td className={styles.measurement}>{formatMetric(data.shp.humidityPercent, "%")}</td>
                      <td className={styles.measurement}>{formatMetric(data.shp.atmosphericPressureHpa, "hPa")}</td>
                      <td className={styles.measurement}>{formatMetric(data.shp.leakageCurrent)}</td>
                      <td className={styles.measurement}>{formatMetric(data.shp.chargeOutputVoltage, "V")}</td>
                      <td>
                        <StatusPill
                          status={data.shp.technicalStatus}
                          label={technicalStatusLabels[data.shp.technicalStatus]}
                        />
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        )}
      </main>
    </div>
  );
}
