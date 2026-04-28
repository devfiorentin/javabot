# ☕ JavaBot

Bot do Telegram para aprender Java de forma gamificada.
Desenvolvido em Java com MySQL, como meu primeiro projeto de bot!

## ✨ Funcionalidades

- 📚 Lições de Java organizadas por dificuldade
- ❓ Quiz com múltipla escolha após cada lição
- ⭐ Sistema de XP e níveis
- 🪙 Moedas por atividade
- 🏆 Ranking global de usuários
- 👤 Perfil com barra de progresso

## 🛠️ Tecnologias

- Java 17
- [TelegramBots 6.x](https://github.com/rubenlagus/TelegramBots)
- MySQL 8
- HikariCP (pool de conexões)
- Maven

## 📁 Estrutura
src/main/java/
└── fiorentin/dev/
├── Main.java
├── bot/
│   ├── MeuBot.java         ← recebe e roteia updates
│   └── CommandHandler.java ← lógica dos comandos
├── db/
│   ├── Database.java       ← conexão com HikariCP
│   ├── UsuarioDAO.java
│   ├── LicaoDAO.java
│   └── PerguntaDAO.java
└── model/
├── Usuario.java
├── Licao.java
├── LicaoProgresso.java
└── Pergunta.java

## 🚀 Como rodar

### Pré-requisitos
- Java 17+
- Maven 3.8+
- MySQL 8+

### 1. Clone o repositório
```bash
git clone https://github.com/devfiorentin/javabot.git
cd javabot
```

### 2. Crie o banco de dados
```bash
mysql -u root -p < banco/schema.sql
```

### 3. Configure as variáveis de ambiente
```bash
cp .env.example .env
```

Edite o `.env`:
```env
BOT_TOKEN=seu_token_aqui
BOT_USERNAME=seu_bot_username
DB_HOST=localhost
DB_PORT=3306
DB_NAME=javabot
DB_USER=root
DB_PASSWORD=sua_senha
```

### 4. Compile e rode
```bash
mvn clean package -q
java -jar target/telegram-bot-1.0-SNAPSHOT.jar
```

## 💬 Comandos

| Comando | Descrição |
|---------|-----------|
| `/start` | Boas-vindas |
| `/aprender` | Próxima lição de Java |
| `/perfil` | Seu nível, XP e progresso |
| `/ranking` | Top 10 jogadores |
| `/sobre` | Informações do bot |
| `/ajuda` | Lista de comandos |

## 📝 Licença

MIT