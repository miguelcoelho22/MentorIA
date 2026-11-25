INSERT INTO themes (id, title, description) VALUES
    ('t07', 'Desafios da mobilidade urbana no Brasil', 'Discuta o acesso ao transporte coletivo e seus efeitos sobre o direito à cidade.'),
    ('t08', 'A inclusão digital para além do acesso à internet', 'Discuta as barreiras de letramento digital e proponha ações para ampliar a autonomia no uso da tecnologia.'),
    ('t09', 'O papel das cidades no combate ao desperdício de alimentos', 'Analise como escolas, feiras e governos locais podem reduzir perdas e fortalecer a segurança alimentar.')
ON CONFLICT DO NOTHING;

INSERT INTO essays (id, student_id, theme_id, text) VALUES
    ('9085f1fe-cac4-4813-bf06-203a68270d82', '4155387b-4518-4657-bc51-d224aa1a69cb', 't07',
     'A mobilidade urbana determina quem consegue chegar à escola e ao trabalho com segurança. Investir em transporte coletivo acessível e medir o tempo de deslocamento por bairro são medidas para reduzir desigualdades.'),
    ('004aecc7-310a-4a63-aafd-4e927c784e2d', 'e96b75e0-42ea-4f46-84db-5fbfb7ff4fe7', 't08',
     'Ter conexão não garante que uma pessoa saiba usar serviços digitais. Bibliotecas públicas podem oferecer oficinas para orientar a população no uso desses serviços e na proteção de dados pessoais.')
ON CONFLICT DO NOTHING;
