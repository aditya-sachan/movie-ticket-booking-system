-- V3: Seed users so bookings have an owner to attribute to during slices 2-4.
-- Password hashes are placeholders: the security slice (slice 5) replaces them with
-- real PasswordEncoder (bcrypt) hashes and wires HTTP Basic authentication.
insert into app_user (username, password_hash, role, enabled) values
    ('admin', 'DEV_PLACEHOLDER_SET_IN_SECURITY_SLICE', 'ADMIN', true),
    ('alice', 'DEV_PLACEHOLDER_SET_IN_SECURITY_SLICE', 'CUSTOMER', true),
    ('bob',   'DEV_PLACEHOLDER_SET_IN_SECURITY_SLICE', 'CUSTOMER', true);
