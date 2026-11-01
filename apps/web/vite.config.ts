import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import basicSsl from '@vitejs/plugin-basic-ssl';

export default defineConfig({
  // 휴대폰 브라우저는 HTTPS에서만 위치를 준다 (npm run dev:web:https)
  plugins: [react(), ...(process.env.HTTPS_DEV ? [basicSsl()] : [])],
  server: {
    host: true, // 같은 와이파이의 휴대폰에서 접속
    proxy: {
      '/api': 'http://localhost:4000',
      '/ws': { target: 'ws://localhost:4000', ws: true },
    },
  },
});
