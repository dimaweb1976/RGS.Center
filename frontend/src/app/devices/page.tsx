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
};

const statusLabels: Record<ConnectionStatus, string> = {
  ONLINE: "В сети",
  OFFLINE: "Нет связи",
  DISABLED: "Отключено"
};

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
                      <span className={`${styles.status} ${styles[device.connectionStatus.toLowerCase()]}`}>
                        <span />{statusLabels[device.connectionStatus]}
                      </span>
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
      </main>
    </div>
  );
}
