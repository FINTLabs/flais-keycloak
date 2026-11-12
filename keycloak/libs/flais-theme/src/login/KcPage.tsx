import "./main.css";
import { Suspense, lazy } from "react";
import type { ClassKey } from "keycloakify/login";
import type { KcContext } from "./KcContext";
import { useI18n } from "./i18n";
import DefaultPage from "keycloakify/login/DefaultPage";
import Template from "keycloakify/login/Template";
import { FlaisOrgSelector } from "./pages/FlaisOrgSelector";
import { FlaisOrgIdpSelector } from "./pages/FlaisOrgIdpSelector";
import { FlaisError } from "./pages/FlaisError";
import { FlaisInfo } from "./pages/FlaisInfo";
import { FlaisLogoutConfirm } from "./pages/FlaisLogoutConfirm";
const UserProfileFormFields = lazy(
  () => import("keycloakify/login/UserProfileFormFields"),
);

const doMakeUserConfirmPassword = true;

export default function KcPage(props: { kcContext: KcContext }) {
  const { kcContext } = props;

  const { i18n } = useI18n({ kcContext });

  return (
    <Suspense>
      {(() => {
        switch (kcContext.pageId) {
          case "info.ftl":
            return <FlaisInfo kcContext={kcContext} i18n={i18n} />;
          case "logout-confirm.ftl":
            return <FlaisLogoutConfirm kcContext={kcContext} i18n={i18n} />;
          case "error.ftl":
            return <FlaisError kcContext={kcContext} i18n={i18n} />;
          case "flais-org-selector.ftl":
            return <FlaisOrgSelector kcContext={kcContext} i18n={i18n} />;
          case "flais-org-idp-selector.ftl":
            return <FlaisOrgIdpSelector kcContext={kcContext} i18n={i18n} />;
          default:
            return (
              <DefaultPage
                kcContext={kcContext}
                i18n={i18n}
                classes={classes}
                Template={Template}
                doUseDefaultCss={true}
                UserProfileFormFields={UserProfileFormFields}
                doMakeUserConfirmPassword={doMakeUserConfirmPassword}
              />
            );
        }
      })()}
    </Suspense>
  );
}

const classes = {} satisfies { [key in ClassKey]?: string };
