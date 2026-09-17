# ForgeOps Feedback Core 2.0

Only two browser packages are supported in 2.0: `@forgeops/feedback-core` and `@forgeops/feedback-dom`.

The host application must expose an authenticated same-origin endpoint that returns a short-lived ForgeOps project token. The token is obtained at request time and is never configured in browser source, local storage, or a build-time variable.

```ts
initForgeOpsFeedback({
  gatewayUrl: 'https://forgeops.internal',
  projectId: 'approved-project-id',
  getToken: async () => {
    const response = await fetch('/api/forgeops/token')
    if (!response.ok) throw new Error('ForgeOps token unavailable')
    return response.text()
  },
})
```

The host issuer must bind the signed token to its authenticated subject, one Registry project ID, the required scope, and a short expiry. The SDK does not provide an anonymous or name-based fallback.
