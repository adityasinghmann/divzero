const apiBase = import.meta.env.VITE_API_BASE;

if (!apiBase) {
  throw new Error('VITE_API_BASE is not defined. Check your .env file.');
}

export const config = {
  apiBase: apiBase as string,
} as const;
