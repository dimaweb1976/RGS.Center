"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getCurrentUser, login } from "@/lib/auth";
import styles from "./login.module.css";

export default function LoginPage() {
  const router = useRouter();
  const [loginValue, setLoginValue] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [checking, setChecking] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let active = true;

    getCurrentUser()
      .then((user) => {
        if (!active) return;
        if (user) {
          router.replace("/devices");
          return;
        }
        setChecking(false);
      })
      .catch(() => {
        if (active) setChecking(false);
      });

    return () => {
      active = false;
    };
  }, [router]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setSubmitting(true);

    try {
      await login(loginValue.trim(), password);
      router.replace("/devices");
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "Не удалось выполнить вход"
      );
      setPassword("");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className={styles.page}>
      <section className={styles.card} aria-labelledby="login-title">
        <div className={styles.brand} aria-hidden="true">RGS</div>
        <h1 id="login-title">Вход в RGSCenter</h1>
        <p className={styles.subtitle}>
          Введите данные учётной записи оператора или сервисного инженера.
        </p>

        {checking ? (
          <p className={styles.checking} role="status">Проверка сессии…</p>
        ) : (
          <form className={styles.form} onSubmit={submit}>
            <label>
              Логин
              <input
                name="login"
                type="text"
                autoComplete="username"
                autoFocus
                required
                value={loginValue}
                onChange={(event) => setLoginValue(event.target.value)}
                disabled={submitting}
              />
            </label>

            <label>
              Пароль
              <input
                name="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                disabled={submitting}
              />
            </label>

            {error && <p className={styles.error} role="alert">{error}</p>}

            <button type="submit" disabled={submitting}>
              {submitting ? "Выполняется вход…" : "Войти"}
            </button>
          </form>
        )}
      </section>
    </main>
  );
}
