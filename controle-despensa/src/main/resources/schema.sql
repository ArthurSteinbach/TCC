-- Schema do banco de dados da Despensa (SQLite)
-- Modelagem: catálogo de produtos separado dos lotes físicos,
-- para permitir múltiplas validades do mesmo produto (FEFO).

CREATE TABLE IF NOT EXISTS produtos (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo_barras   TEXT NOT NULL UNIQUE,
    nome            TEXT NOT NULL,
    categoria       TEXT,
    unidade_medida  TEXT NOT NULL DEFAULT 'UN',
    data_cadastro   TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

CREATE TABLE IF NOT EXISTS lotes (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    produto_id      INTEGER NOT NULL,
    quantidade      REAL NOT NULL,
    preco_unitario  REAL NOT NULL,
    data_validade   TEXT NOT NULL,          -- formato ISO: YYYY-MM-DD
    data_cadastro   TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    status          TEXT NOT NULL DEFAULT 'ATIVO'
                        CHECK (status IN ('ATIVO', 'CONSUMIDO', 'DESCARTADO')),
    data_baixa      TEXT,                   -- preenchida quando sai do status ATIVO
    FOREIGN KEY (produto_id) REFERENCES produtos(id)
);

-- Acelera a consulta que ordena os lotes ativos por proximidade de validade (FEFO)
CREATE INDEX IF NOT EXISTS idx_lotes_status_validade
    ON lotes (status, data_validade);

-- Acelera a busca de lotes de um produto específico
CREATE INDEX IF NOT EXISTS idx_lotes_produto
    ON lotes (produto_id);
