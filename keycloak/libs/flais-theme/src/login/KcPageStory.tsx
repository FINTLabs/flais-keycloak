import type { DeepPartial } from "keycloakify/tools/DeepPartial";
import type { KcContext } from "./KcContext";
import KcPage from "./KcPage";
import { createGetKcContextMock } from "keycloakify/login/KcContext";
import type {
  KcContextExtension,
  KcContextExtensionPerPage,
} from "./KcContext";
import { themeNames, kcEnvDefaults } from "../kc.gen";

const kcContextExtension: KcContextExtension = {
  themeName: themeNames[0],
  properties: {
    ...kcEnvDefaults,
  },
};
const kcContextExtensionPerPage: KcContextExtensionPerPage = {
  "flais-org-selector.ftl": {
    organizations: [
      {
        alias: "frid",
        name: "FRID IKS",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/frid_iks.svg"
      },
      {
        alias: "osloskolen",
        name: "Osloskolen",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/oslo_skolen.svg"
      },
      {
        alias: "finnmark",
        name: "Finnmark",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/finnmark.svg"
      },
      {
        alias: "troms",
        name: "Troms",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/troms.svg"
      },
      {
        alias: "telemark",
        name: "Telemark",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/telemark.svg"
      },
      {
        alias: "vestfold",
        name: "Vestfold",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/vestfold.svg"
      },
      {
        alias: "ostfold",
        name: "Østfold",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/ostfold.svg"
      },
      {
        alias: "buskerud",
        name: "Buskerud",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/buskerud.svg"
      },
      {
        alias: "akershus",
        name: "Akershus",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/akershus.svg"
      },
      {
        alias: "innlandet",
        name: "Innlandet",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/innlandet.svg"
      },
      {
        alias: "nordland",
        name: "Nordland",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/nordland.svg"
      },
      {
        alias: "more-og-romsdal",
        name: "Møre og Romsdal",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/more-og-romsdal.svg"
      },
      {
        alias: "agder",
        name: "Agder",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/agder.svg"
      },
      {
        alias: "trondelag",
        name: "Trøndelag",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/trondelag.svg"
      },
      {
        alias: "vestland",
        name: "Vestland",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/vestland.svg"
      },
      {
        alias: "rogaland",
        name: "Rogaland",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/rogaland.svg"
      },
      {
        alias: "viken",
        name: "Viken",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/frid_iks.svg"
      },
      {
        alias: "novari",
        name: "Novari IKS",
        logo: "https://flaispublicresources.blob.core.windows.net/keycloak/novari_iks.svg"
      },
    ],
    url: {
      registrationAction: "",
    },
  },
  "flais-org-idp-selector.ftl": {
    providers: [
      {
        alias: "test",
        name: "Test",
      },
      {
        alias: "test2",
        name: "Test 2",
      },
    ],
    url: {
      registrationAction: "",
    },
  },
};

export const { getKcContextMock } = createGetKcContextMock({
  kcContextExtension,
  kcContextExtensionPerPage,
  overrides: {},
  overridesPerPage: {},
});

export function createKcPageStory<PageId extends KcContext["pageId"]>(params: {
  pageId: PageId;
}) {
  const { pageId } = params;

  function KcPageStory(props: {
    kcContext?: DeepPartial<Extract<KcContext, { pageId: PageId }>>;
  }) {
    const { kcContext: overrides } = props;

    const kcContextMock = getKcContextMock({
      pageId,
      overrides,
    });

    return <KcPage kcContext={kcContextMock} />;
  }

  return { KcPageStory };
}
