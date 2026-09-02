type CsrfMetadata = {
  cookieName: string;
  headerName: string;
};

export type CurrentUser = {
  username: string;
  role: string;
  connected: number;
  multipleConnectionsWarning: boolean;
};

function readCookie(name: string): string | null {
  const prefix = `${encodeURIComponent(name)}=`;
  const cookie = document.cookie
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix));

  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

async function problemMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { detail?: string; title?: string };
    return body.detail ?? body.title ?? `Ошибка HTTP ${response.status}`;
  } catch {
    return `Ошибка HTTP ${response.status}`;
  }
}

async function csrfHeaders(): Promise<Record<string, string>> {
  const response = await fetch("/api/v1/auth/csrf", {
    credentials: "include",
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error(await problemMessage(response));
  }

  const metadata = (await response.json()) as CsrfMetadata;
  const token = readCookie(metadata.cookieName);
  if (!token) {
    throw new Error("Сервер не установил CSRF cookie");
  }

  return { [metadata.headerName]: token };
}

export async function login(loginValue: string, password: string): Promise<void> {
  const headers = await csrfHeaders();
  const response = await fetch("/api/v1/auth/login", {
    method: "POST",
    credentials: "include",
    cache: "no-store",
    headers: {
      "Content-Type": "application/json",
      ...headers
    },
    body: JSON.stringify({ login: loginValue, password })
  });

  if (!response.ok) {
    if (response.status === 401) {
      throw new Error("Неверный логин или пароль");
    }
    throw new Error(await problemMessage(response));
  }
}

export async function getCurrentUser(): Promise<CurrentUser | null> {
  const response = await fetch("/api/v1/auth/me", {
    credentials: "include",
    cache: "no-store"
  });

  if (response.status === 401) {
    return null;
  }
  if (!response.ok) {
    throw new Error(await problemMessage(response));
  }

  return response.json() as Promise<CurrentUser>;
}

export async function logout(): Promise<void> {
  const headers = await csrfHeaders();
  const response = await fetch("/api/v1/auth/logout", {
    method: "POST",
    credentials: "include",
    cache: "no-store",
    headers
  });

  if (!response.ok) {
    throw new Error(await problemMessage(response));
  }
}
