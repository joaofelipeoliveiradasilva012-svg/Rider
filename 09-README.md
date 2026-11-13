# Chama Rider 2.0

Atualização do jogo Android nativo (Kotlin), pronta para compilar pelo GitHub Actions.

## O que mudou
- Cavaleiro esqueleto pilotando uma moto infernal.
- Botões dedicados para disparar projéteis e usar dash/esquiva.
- Cenário de estrada infernal com montanhas, lava e iluminação.
- Inimigos terrestres, voadores, ágeis e demônio pesado.
- Fases automáticas por distância, com aumento progressivo da dificuldade.
- HUD com vida, pontuação, fase e distância.
- Música original sintetizada em loop.

## Gerar APK pelo GitHub no celular
1. Envie o conteúdo desta pasta para a raiz do repositório `chama-rider` (substituindo os arquivos antigos).
2. Abra a aba **Actions** do repositório.
3. Selecione **Build Chama Rider APK** e toque em **Run workflow** (ou aguarde o build automático após o push).
4. Quando terminar com sucesso, abra a execução e baixe **chama-rider-debug-apk** em Artifacts.
5. Extraia o ZIP baixado e instale `app-debug.apk`.

O workflow gera um APK debug instalável. Não é necessário configurar chave de assinatura para esse teste.
