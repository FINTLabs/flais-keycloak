import { memo, type FC } from "react";
import { allExpanded, darkStyles, JsonView } from "react-json-view-lite";
import Keycloak from "keycloak-js";

interface IClaimsViewerProps {
  keycloak: Keycloak;
}

const ClaimsViewer: FC<IClaimsViewerProps> = ({ keycloak }) => {
  const claims = keycloak.tokenParsed || {};
  return (
    <div>
      <h2>User Claims</h2>
      <JsonView
        data={claims}
        shouldExpandNode={allExpanded}
        style={darkStyles}
      />
    </div>
  );
};

export default memo(ClaimsViewer);
