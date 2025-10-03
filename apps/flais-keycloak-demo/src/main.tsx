import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import Keycloak from 'keycloak-js'

// Initialize Keycloak
const keycloak = new Keycloak({
  url: 'https://keycloak.test.infra.flais.no',
  realm: 'external',
  clientId: 'flais-keycloak-demo',
})

keycloak.init().then((authenticated) => {
  if (authenticated) {
    createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <App keycloak={keycloak} />
      </StrictMode>
    )
  } else {
    keycloak.login()
  }
})
