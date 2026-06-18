# Introduction 
TODO: Give a short introduction of your project. Let this section explain the objectives or the motivation behind this project. 

# Getting Started
TODO: Guide users through getting your code up and running on their own system. In this section you can talk about:
1.	Installation process
2.	Software dependencies
3.	Latest releases
4.	API references

# Build and Test
TODO: Describe and show how to build your code and run the tests. 

# Traducción de mensajes de validación (Google Cloud Translation)

El endpoint `POST /validate` devuelve los mensajes del motor DMN (Drools/KIE) en **inglés**.
Opcionalmente, el microservicio puede **traducir dinámicamente** esos mensajes a otros idiomas
usando la API V2 de [Google Cloud Translation](https://cloud.google.com/translate).

## Cómo funciona

- El idioma destino se elige por petición mediante la cabecera HTTP **`Accept-Language`**
  (ej. `Accept-Language: es`). Acepta también códigos con región (`es-CO` → `es`).
- Solo se traduce el campo `text` de cada mensaje. **No** se traducen:
  - `rawDetail` (mensaje técnico original, útil para logs/debug),
  - `drgElementName` (nombre que define el usuario en el diagrama),
  - `type` / `level` (códigos internos).
- Eficiencia y robustez:
  - Los textos únicos de una respuesta se traducen en **una sola llamada** (batch).
  - Hay **caché en memoria** (los mensajes de validación se repiten mucho).
  - **Degradación elegante**: si la traducción está deshabilitada, el idioma no está soportado,
    el destino es el idioma origen (`en`), o la API falla, se devuelven los mensajes en inglés
    sin romper la validación.

## Configuración

Propiedades (en `application.properties`, sobreescribibles por variable de entorno):

| Propiedad | Variable de entorno | Default | Descripción |
|---|---|---|---|
| `translation.enabled` | `TRANSLATION_ENABLED` | `false` | Activa la traducción. |
| `translation.source-language` | — | `en` | Idioma en que el motor DMN emite los mensajes. |
| `translation.default-language` | — | `en` | Idioma usado si el cliente no manda `Accept-Language` válido. |
| `translation.supported-languages` | — | `en,es,pt,fr,de` | Idiomas permitidos (uno fuera de la lista queda en inglés). |
| `translation.cache-enabled` | — | `true` | Caché en memoria de traducciones. |
| `translation.cache-max-entries` | — | `5000` | Tope de la caché; al superarse se limpia. |
| `translation.api-key` | `GOOGLE_TRANSLATE_API_KEY` | _(vacío)_ | API key (Opción B). Vacío ⇒ usa cuenta de servicio. |

### Credenciales de Google Cloud

1. En Google Cloud Console: crea/selecciona un proyecto y habilita **Cloud Translation API**.
2. Elige un método de autenticación:
   - **Opción A — Cuenta de servicio (recomendada):** crea una Service Account con el rol
     *Cloud Translation API User*, descarga su JSON y exponlo vía la variable de entorno
     `GOOGLE_APPLICATION_CREDENTIALS` (Application Default Credentials).
   - **Opción B — API key (más simple, menos segura):** fija `GOOGLE_TRANSLATE_API_KEY`.

> ⚠️ El JSON de la cuenta de servicio y cualquier `*-key.json` están en `.gitignore`. **Nunca**
> los subas al repositorio.

## Uso con Docker

El `docker-compose.yml` ya expone las variables de traducción. Por defecto está **desactivada**.

Para activarla con **cuenta de servicio (Opción A)**:

1. Coloca tu JSON en `./secrets/gcp-translate-key.json`.
2. Descomenta el bloque `volumes` del servicio `dmn-sidecar` en `docker-compose.yml`:
   ```yaml
   volumes:
     - ./secrets/gcp-translate-key.json:/secrets/gcp-translate-key.json:ro
   ```
3. Levanta el servicio con la traducción activa:
   ```bash
   TRANSLATION_ENABLED=true docker compose up -d --build
   ```

Para activarla con **API key (Opción B)** no necesitas el volumen:
```bash
TRANSLATION_ENABLED=true GOOGLE_TRANSLATE_API_KEY=tu_api_key docker compose up -d --build
```

## Ejemplo

```bash
curl -X POST http://localhost:8080/validate \
  -H "Content-Type: application/json" \
  -H "Accept-Language: es" \
  -d '{"xml":"<...diagrama DMN...>"}'
```

Sin la cabecera `Accept-Language` (o con `en`), la respuesta llega en inglés.

## Detalles de implementación

| Componente | Responsabilidad |
|---|---|
| `config/TranslationProperties` | Propiedades `translation.*`. |
| `config/TranslationConfig` | Crea el cliente `Translate` (solo si `translation.enabled=true`). |
| `translator/MessageTranslationService` | Traduce la `ValidationResponse` (batch + caché + fallback). |
| `service/DmnValidationService` | `validate(xml, targetLanguage)` aplica la traducción al final. |
| `controller/ValidationController` | Resuelve el idioma desde `Accept-Language`. |

> Nota: la clase preexistente `translator/DmnMessageTranslator` **no** traduce idiomas; mapea la
> estructura del mensaje del motor KIE al modelo `ValidationMessage`. La traducción de idioma la
> añade `MessageTranslationService`.

# Contribute
TODO: Explain how other users and developers can contribute to make your code better. 

If you want to learn more about creating good readme files then refer the following [guidelines](https://docs.microsoft.com/en-us/azure/devops/repos/git/create-a-readme?view=azure-devops). You can also seek inspiration from the below readme files:
- [ASP.NET Core](https://github.com/aspnet/Home)
- [Visual Studio Code](https://github.com/Microsoft/vscode)
- [Chakra Core](https://github.com/Microsoft/ChakraCore)