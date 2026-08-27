ALTER TABLE estabelecimento
DROP CONSTRAINT IF EXISTS chk_estabelecimento_tema;

ALTER TABLE estabelecimento
    ADD CONSTRAINT chk_estabelecimento_tema
        CHECK (
            tema IN (
                     'artesanal',
                     'brasa',
                     'atlantico',
                     'vinho',
                     'grafite'
                )
            );