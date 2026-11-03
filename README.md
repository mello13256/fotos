# Fotos — gestor multimédia para Android

Não é "mais uma galeria". É uma galeria completa que substitui a do sistema e, por cima dela, um gestor de fotografias a sério: organização, pesquisa e privacidade, tudo processado no dispositivo.

## Âmbito

Ecossistema completo (galeria + gestor), construído **por fases**. A primeira fase tem de ser uma galeria excelente, porque um gestor que não é a galeria predefinida acaba por não ser aberto, e as funcionalidades avançadas ficam esquecidas.

## Princípio central: coleções virtuais

O ficheiro original nunca é copiado nem movido para organizar. Álbuns, etiquetas, favoritos e pastas inteligentes são **referências** guardadas numa base de dados local.

- Uma fotografia em `DCIM/Camera` pode estar em "Favoritas", "Viagens" e "Memórias de 2026" sem ocupar espaço extra.
- **Atenção:** o `_ID` do MediaStore não é estável (muda após reindexação, restauro de cópia de segurança ou troca de cartão SD). Cada item tem uma identidade própria (`caminho relativo + tamanho + data` e, como reserva, hash do conteúdo), reconciliada sempre que o MediaStore muda. Sem isto, as coleções "perdem" fotografias com o tempo.
- Pastas inteligentes são consultas guardadas (ex.: `tipo = captura de ecrã AND ano = 2025`), não listas fixas.

## Pilares e fases

### Fase 1 — Galeria (MVP)
- Grelha rápida sobre o MediaStore (paginada, fluida com 50 000+ itens), tamanho ajustável por gesto de pinça.
- Visualizador limpo (zoom, deslizar, vídeo).
- Álbuns por pasta + coleções virtuais + favoritos.
- Seleção múltipla rápida (arrastar para selecionar intervalos).
- Reciclagem com recuperação.
- Separação automática: fotografias / vídeos / capturas de ecrã (por pasta e metadados).
- Temas claro, escuro e Material You.

### Fase 2 — Organização
- Etiquetas personalizadas.
- Pastas inteligentes (consultas guardadas, filtros combináveis).
- Duplicados exatos (hash) e quase duplicados (hash percetual — rajadas, edições, reenvios de WhatsApp), com eliminação em lote.
- Organização por data e localização (EXIF), mapa.
- Histórico de alterações (o que foi movido, eliminado, etiquetado — com anular).

### Fase 3 — Privacidade
- Cofre com `BiometricPrompt`. Os ficheiros são **movidos e cifrados** para o armazenamento privado da app (esconder com `.nomedia` não protege nada: outras apps continuam a ver os ficheiros).
- Álbuns ocultos (só escondidos da vista, sem cifra — diferente do cofre).
- Zero rede por omissão: a app não pede permissão de Internet nas compilações sem sincronização.

### Fase 4 — Pesquisa inteligente (tudo no dispositivo)
- Indexação em segundo plano (`WorkManager`, preferencialmente a carregar e com ecrã desligado).
- Pesquisa semântica em linguagem natural com um modelo tipo CLIP no dispositivo: "gatos em 2025" passa a ser `embedding("gato") ∩ ano = 2025`. Cobre objetos, cenas e boa parte das "cores" sem modelos separados.
- Texto nas imagens (OCR) — útil para documentos e capturas de ecrã.
- Pessoas: deteção de rostos + embeddings + agrupamento; o utilizador dá nome aos grupos.
- Classificação de documentos e memes.

### Fase 5 — Refinamento
- Navegação inferior personalizável, gestos configuráveis, temas personalizados.
- Edição não destrutiva (pilha de edições guardada, original intacto).
- Partilha rápida com contactos/apps frequentes.

## Realidades do Android a ter em conta

| Tema | Implicação |
|---|---|
| Permissões (Android 13+) | `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`. No Android 14+ o utilizador pode conceder acesso **parcial** (`READ_MEDIA_VISUAL_USER_SELECTED`); a app tem de funcionar e explicar bem nesse caso. |
| Eliminar/mover em lote | `MediaStore.createDeleteRequest` / `createWriteRequest` pedem confirmação do sistema. Com `MANAGE_MEDIA` (Android 12+, concedida pelo utilizador nas definições) a confirmação desaparece — essencial para a "praticidade". |
| Ser galeria predefinida | Registar `intent-filter` para `ACTION_VIEW` e `image/*`, `video/*`; tratar URIs `content://` vindos de outras apps. |
| Desempenho | Miniaturas via `ContentResolver.loadThumbnail`, cache em disco, Paging 3. A grelha é o ecrã mais usado — não pode falhar um único frame. |

## Arquitetura proposta

- **Kotlin + Jetpack Compose** (nativo: o MediaStore, as permissões e o desempenho da grelha justificam não usar Flutter/React Native).
- **Room** — coleções virtuais, etiquetas, índice de pesquisa, histórico.
- **Paging 3 + Coil** — grelha e miniaturas.
- **Media3 / ExoPlayer** — vídeo.
- **WorkManager** — indexação, hashes, ML.
- **LiteRT (TensorFlow Lite) / ML Kit** — modelos no dispositivo.
- **Tink / Jetpack Security** — cifra do cofre.

Módulos: `core:media` (acesso ao MediaStore e reconciliação de identidades), `core:data` (Room), `feature:grid`, `feature:viewer`, `feature:collections`, `feature:vault`, `feature:search`, `indexer`.

## Fora de âmbito (por agora)

Sincronização na nuvem, versão para computador, partilha de álbuns entre utilizadores.
