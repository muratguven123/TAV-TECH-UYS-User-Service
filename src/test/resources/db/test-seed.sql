-- IT testleri için rol seed verisi.
-- ddl-auto: create-drop ile Hibernate şemayı oluşturur; roller bu script ile eklenir.
INSERT INTO roles (name) VALUES ('OPERATION_OFFICER'), ('BI_SPECIALIST'), ('ADMIN')
ON CONFLICT (name) DO NOTHING;
