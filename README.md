# Fotos — gestor multimédia para Android

Não é "mais uma galeria". É uma galeria completa que substitui a do sistema e, por cima dela, um gestor de fotografias a sério: organização, pesquisa e privacidade, tudo processado no dispositivo.

## Estado

Fase 1 em curso. Já existe:

- Índice local da biblioteca sincronizado com o MediaStore, com identidade estável por item (ver abaixo).
- Grelha com cabeçalhos por mês, pinça para mudar o número de colunas e filtros (tudo, fotografias, vídeos, capturas de ecrã).
- Seleção múltipla: toque longo e arrastar para selecionar intervalos, com deslocamento automático nas margens.
- Coleções virtuais e favoritos, pastas do dispositivo, partilha, reciclagem do sistema (restaurar / eliminar definitivamente).
- Visualizador com deslizar, pinça, toque duplo e reprodução de vídeo.
- Abrir fotografias e vídeos vindos de outras apps (pode ser escolhida como galeria predefinida).
- Tema claro, escuro e Material You. Acesso parcial do Android 14 tratado.

## Compilar e instalar

Requisitos: JDK 17+ e Android SDK (API 36).

```bash
./gradlew :core:model:test      # testes da lógica de identidade e classificação
./gradlew :app:assembleDebug    # APK em app/build/outputs/apk/debug/
```

Cada envio para o GitHub corre o workflow **Android**, que executa os testes e o lint e publica o APK de depuração como artefacto (`fotos-debug-apk`) na página da execução. Basta descarregá-lo e instalá-lo no telemóvel (Android 11 ou superior).

O esquema da base de dados é exportado para `app/schemas/` na primeira compilação local; deve ser incluído no repositório para permitir migrações.

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

## Arquitetura

- **Android 11+ (API 30)**: reciclagem do sistema, pedidos de eliminação em lote e armazenamento delimitado disponíveis em todas as versões suportadas.
- **Kotlin + Jetpack Compose** (nativo: o MediaStore, as permissões e o desempenho da grelha justificam não usar Flutter/React Native).
- **Room** — coleções virtuais, etiquetas, índice de pesquisa, histórico.
- **Paging 3 + Coil** — grelha e miniaturas.
- **Media3 / ExoPlayer** — vídeo.
- **WorkManager** — indexação, hashes, ML.
- **LiteRT (TensorFlow Lite) / ML Kit** — modelos no dispositivo.
- **Tink / Jetpack Security** — cifra do cofre.

Módulos:

- `core:model` — Kotlin puro, sem Android: reconciliação de identidades (`Reconciler`) e classificação de itens (`MediaKind`). Testado na JVM.
- `app` — dados (`data/`: Room, leitura do MediaStore, sincronização, reciclagem, definições) e interface (`ui/`: grelha, visualizador, álbuns, coleções, reciclagem, definições).

À medida que as fases avançarem, as funcionalidades maiores (cofre, pesquisa, indexação) passam a módulos próprios.

## Fora de âmbito (por agora)

Sincronização na nuvem, versão para computador, partilha de álbuns entre utilizadores.
