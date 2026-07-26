-- Dev-only seed account so there's a way to log in at all before any registration flow
-- exists. Password: AlphaGraph@2026 (bcrypt, cost 10). Rotate before anything but local dev.
INSERT INTO api.platform_users (email, password_hash, role, active)
VALUES ('admin@alphagraph.local', '$2a$10$sgmchq0Ry4t5amnem4hHvec32ArPdy00NweBSGoVdUV9uiRxgAjV2', 'ADMIN', true)
ON CONFLICT (email) DO NOTHING;
