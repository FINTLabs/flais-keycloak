import "./App.css";
import Keycloak from "keycloak-js";
import { memo, type FC } from "react";
import ClaimsViewer from "./components/claimsViewer";

interface IAppProps {
  keycloak: Keycloak;
}

const App: FC<IAppProps> = ({ keycloak }) => {
  return (
    <>
      <ClaimsViewer keycloak={keycloak} />
    </>
  );
};

export default memo(App);
