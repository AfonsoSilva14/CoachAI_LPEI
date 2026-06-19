Configuração do Ambiente

Antes de executar o projeto, é necessário criar e ativar um ambiente virtual Python (venv) para garantir que todas as dependências são instaladas corretamente e evitar conflitos com outras versões de bibliotecas existentes no sistema.

1. Criar o ambiente virtual
py -3.11 -m venv venv
2. Ativar o ambiente virtual
Windows (PowerShell)
.\venv\Scripts\Activate.ps1

Após a ativação deverá aparecer (venv) no início da linha de comandos.

3. Instalar as dependências
pip install opencv-python mediapipe numpy tensorflow==2.15.0 rfdetr
4. Executar o projeto
python comparar_modelos.py

O projeto irá processar o vídeo definido em VIDEO_PATH e gerar automaticamente os vídeos resultantes para cada modelo na pasta outputs/.

Importante: A pasta venv não deve ser enviada para o GitHub, uma vez que é gerada localmente em cada computador.