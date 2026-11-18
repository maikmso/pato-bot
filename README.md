# 🦆 PATO BLOX FRUITS — bot de vendas

Bot do Discord que vende frutas do Blox Fruits com **PIX automático pela AmploPay**.

## Como funciona

1. O cliente clica em **🛒 Comprar** no `#comprar` e escolhe a fruta.
2. Preenche o formulário: nick do Roblox, nome, e-mail, CPF/celular (opcionais).
3. O bot **reserva a fruta**, abre um **ticket privado** e posta o **QR Code + PIX copia e cola**.
4. A AmploPay avisa o bot (webhook) → o bot **confere na API** → manda **✅ Pagamento confirmado** e marca você.
5. Você faz o trade no jogo e clica em **📦 Marcar como entregue** → sai no `#entregas-feitas`.
6. Se o cliente não pagar em 30 min, o pedido é cancelado e a fruta volta ao estoque.

## Comandos (só administradores)

| Comando | O que faz |
|---|---|
| `/fruta adicionar nome preco estoque` | Cadastra uma fruta |
| `/fruta preco nome preco` | Muda o preço |
| `/fruta estoque nome quantidade` | Muda o estoque |
| `/fruta remover nome` | Tira da loja (zera o estoque) |
| `/fruta listar` | Lista tudo (só você vê) |
| `/painel loja` | Posta o botão **Comprar** no canal atual |
| `/painel estoque` | Posta a lista de preços no canal atual |

## Estrutura

```
amplopay/   cliente HTTP da AmploPay (criar PIX, consultar transação)
discord/    JDA: comandos, botões, formulário, tickets (ShopService)
domain/     entidades: Fruit, PurchaseOrder
service/    regras de pedido, webhook, expiração, QR Code
web/        endpoint POST /webhooks/amplopay e GET /health
```

## Segurança

- Webhook validado pelo **token exclusivo de cada pedido** (`isStrictWebhookToken`).
- Mesmo com token válido, o bot **consulta a transação na API** antes de liberar.
- Estoque reservado com **lock no banco**, então a mesma fruta não é vendida duas vezes.
- Nenhuma chave fica no código: tudo vem de variáveis de ambiente (veja `.env.example`).

## Deploy (Render + Neon)

1. **Neon**: crie um banco e copie host, usuário e senha (formato JDBC em `DB_URL`).
2. **GitHub**: suba este projeto num repositório **privado**.
3. **Render** → New → Web Service → conecte o repositório → Runtime **Docker**.
4. Em **Environment**, preencha as variáveis do `.env.example`.
5. Depois do primeiro deploy, ajuste `AMPLOPAY_CALLBACK_URL` com a URL real do Render.
6. No Discord, rode `/painel loja` no `#comprar` (apague o painel antigo do Ticket Tool).

⚠️ **Render free "dorme"** após 15 min sem acesso, e o bot sai do ar. Para evitar,
use um monitor gratuito (ex.: UptimeRobot) chamando `https://SEU-APP.onrender.com/health`
a cada 5 minutos, ou o plano pago do Render.

## Rodar localmente

```bash
# Windows PowerShell: defina as variáveis com $env:NOME="valor"
mvn spring-boot:run
```

Testes: `mvn test`
