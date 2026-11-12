import React, {
  type ChangeEvent,
  type HTMLAttributes,
  type KeyboardEvent,
  type PointerEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { Chevron } from "./icons/Chevron.tsx";
import { I18n } from "../i18n.ts";

export interface LogoOption {
  id: string;
  label: string;
  logosUrl?: string;
}

export interface LogoDropdownInputProps extends Omit<
  HTMLAttributes<HTMLDivElement>,
  "onChange"
> {
  name: string;
  options: LogoOption[];
  value: string | null;
  onChange: (id: string) => void;
  placeholder: string;
  i18n: I18n;
  hasError: boolean;
  errorId: string;
}

const LogoDropdownInputComponent = ({
  id,
  name,
  options,
  value,
  onChange,
  placeholder,
  i18n,
  hasError,
  errorId,
  className,
  ...rest
}: LogoDropdownInputProps) => {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [isFiltering, setIsFiltering] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);

  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listboxRef = useRef<HTMLUListElement>(null);

  const controlId = id ?? name;
  const listboxId = `${controlId}-listbox`;

  const selected = useMemo(
    () => options.find((option) => option.id === value) ?? null,
    [options, value],
  );

  const filteredOptions = useMemo(() => {
    const search = query.trim().toLowerCase();

    if (!isFiltering || !search) return options;

    return options.filter((option) =>
      option.label.trim().toLowerCase().includes(search),
    );
  }, [options, query, isFiltering]);

  const activeOption = filteredOptions[activeIndex];
  const inputValue = isFiltering ? query : (selected?.label ?? "");

  const openList = (filtering = false) => {
    const selectedIndex = filteredOptions.findIndex(
      (option) => option.id === value,
    );

    setActiveIndex(
      selectedIndex >= 0 ? selectedIndex : filteredOptions.length > 0 ? 0 : -1,
    );
    setIsFiltering(filtering);
    setOpen(true);
  };

  const closeList = useCallback((resetQuery = true) => {
    setOpen(false);
    setActiveIndex(-1);
    setIsFiltering(false);

    if (!resetQuery) return;

    setQuery("");
  }, []);

  const selectOption = (option: LogoOption) => {
    onChange(option.id);
    setQuery("");
    setIsFiltering(false);
    closeList(false);
  };

  const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const nextInputValue = event.target.value;
    const selectedLabel = selected?.label ?? "";
    const shouldRemoveSelectedLabel =
      selected &&
      !isFiltering &&
      selectedLabel &&
      nextInputValue.startsWith(selectedLabel);

    setIsFiltering(true);
    setQuery(
      shouldRemoveSelectedLabel
        ? nextInputValue.slice(selectedLabel.length)
        : nextInputValue,
    );
    setOpen(true);

    if (selected) {
      onChange("");
    }
  };

  const handleInputKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    switch (event.key) {
      case "ArrowDown":
      case "ArrowUp": {
        event.preventDefault();

        if (!open) {
          openList();
          return;
        }

        setActiveIndex((currentIndex) => {
          if (filteredOptions.length === 0) return -1;

          const direction = event.key === "ArrowDown" ? 1 : -1;

          return (
            (currentIndex + direction + filteredOptions.length) %
            filteredOptions.length
          );
        });

        break;
      }

      case "Enter":
        event.preventDefault();

        if (!open) {
          openList();
          return;
        }

        if (activeOption) {
          selectOption(activeOption);
        }

        break;

      case "Escape":
        event.preventDefault();
        closeList();
        break;

      case "Tab":
        closeList(false);
        break;
    }
  };

  useEffect(() => {
    const handlePointerDown = (event: globalThis.PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) {
        closeList();
      }
    };

    document.addEventListener("pointerdown", handlePointerDown);

    return () => document.removeEventListener("pointerdown", handlePointerDown);
  }, [closeList]);

  useEffect(() => {
    if (!open || activeIndex < 0) return;

    listboxRef.current
      ?.querySelector<HTMLElement>(`[data-option-index="${activeIndex}"]`)
      ?.scrollIntoView({ block: "nearest" });
  }, [open, activeIndex]);

  return (
    <div
      ref={containerRef}
      className={`relative w-full text-left ${className ?? ""}`}
      {...rest}
    >
      {hasError && (
        <p
          id={errorId}
          role="alert"
          className="mb-3 flex items-start gap-2 text-sm font-medium leading-relaxed text-error"
        >
          <span aria-hidden="true">!</span>
          {i18n.msgStr("chooseOrgRequired")}
        </p>
      )}

      <input type="hidden" name={name} value={value ?? ""} />

      <div
        onPointerDown={(event: PointerEvent<HTMLDivElement>) => {
          // Let the input handle native focus, caret placement, and selection.
          if (event.target !== inputRef.current) {
            event.preventDefault();
            inputRef.current?.focus();
          }
          openList();
        }}
        className={`
  flex min-h-14 w-full cursor-text items-center gap-3 rounded-md border
  bg-surface px-3 py-2 text-left text-base text-content-muted hover:bg-surface-hover
  focus-within:z-10 focus-within:outline-none sm:min-h-14 sm:px-4
  ${
    hasError
      ? "border-error-border ring-1 ring-error-border focus-within:border-error-border focus-within:ring-error-border"
      : "border-border-input focus-within:border-focus focus-within:ring-1 focus-within:ring-focus"
  }
`}
      >
        {selected?.logosUrl && !isFiltering && (
          <span className="organization-logo">
            <img
              src={selected.logosUrl}
              alt=""
              aria-hidden="true"
              className="max-h-full max-w-full object-contain"
            />
          </span>
        )}

        {(!selected?.logosUrl || isFiltering) && (
          <svg
            aria-hidden="true"
            className="h-5 w-5 shrink-0 text-content-subtle"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth={1.5}
          >
            <circle cx="10.5" cy="10.5" r="6.5" />
            <path d="m16 16 4.5 4.5" strokeLinecap="round" />
          </svg>
        )}

        <input
          ref={inputRef}
          id={controlId}
          type="text"
          role="combobox"
          aria-describedby={hasError ? errorId : undefined}
          aria-invalid={hasError}
          aria-controls={listboxId}
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-autocomplete="list"
          aria-activedescendant={
            open && activeOption ? `${listboxId}-${activeOption.id}` : undefined
          }
          value={inputValue}
          placeholder={placeholder}
          autoComplete="off"
          onFocus={() => openList()}
          onChange={handleInputChange}
          onKeyDown={handleInputKeyDown}
          className="
            min-w-0 flex-1 bg-transparent text-content-muted placeholder:text-placeholder
            focus:outline-none
          "
        />

        <button
          type="button"
          tabIndex={-1}
          aria-label={open ? i18n.msgStr("closeList") : i18n.msgStr("openList")}
          onPointerDown={(event: PointerEvent<HTMLButtonElement>) => {
            event.preventDefault();
            event.stopPropagation();

            setOpen((isOpen) => {
              const nextOpen = !isOpen;

              if (nextOpen) {
                setIsFiltering(false);
              }

              return nextOpen;
            });

            inputRef.current?.focus();
          }}
          className="shrink-0"
        >
          <Chevron
            direction={open ? "up" : "down"}
            aria-hidden="true"
            className="h-4 w-4 text-content-subtle"
          />
        </button>
      </div>

      {open && (
        <ul
          ref={listboxRef}
          id={listboxId}
          role="listbox"
          aria-labelledby={controlId}
          className="
            absolute z-10 mt-1 max-h-50 w-full overflow-auto rounded-md bg-surface
            shadow-lg shadow-shadow ring-1 ring-dropdown-ring focus:outline-none sm:max-h-56
          "
        >
          {filteredOptions.length === 0 ? (
            <li className="px-3 py-2.5 text-base text-content-subtle sm:px-4 sm:py-3">
              {i18n.msgStr("noResult")}
            </li>
          ) : (
            filteredOptions.map((option, index) => {
              const isSelected = option.id === value;
              const isActive = index === activeIndex;

              return (
                <li
                  key={option.id}
                  id={`${listboxId}-${option.id}`}
                  role="option"
                  aria-selected={isSelected}
                  data-option-index={index}
                  onMouseEnter={() => setActiveIndex(index)}
                  onPointerDown={(event: PointerEvent<HTMLLIElement>) => {
                    event.preventDefault();
                    selectOption(option);
                  }}
                  className={`
                    flex w-full cursor-pointer items-center px-3 py-2.5 text-left text-base
                    focus:outline-none sm:px-4 sm:py-3
                    ${
                      isSelected
                        ? "bg-surface-selected font-semibold text-content-selected"
                        : "text-content-muted hover:bg-surface-hover"
                    }
                    ${isActive && !isSelected ? "bg-surface-hover" : ""}
                  `}
                >
                  {option.logosUrl && (
                    <span className="organization-logo">
                      <img
                        src={option.logosUrl}
                        alt=""
                        aria-hidden="true"
                        className="max-h-full max-w-full object-contain"
                      />
                    </span>
                  )}

                  <span className="ml-3 min-w-0 flex-1 wrap-break-word">
                    {option.label}
                  </span>
                </li>
              );
            })
          )}
        </ul>
      )}
    </div>
  );
};

export const LogoDropdownInput = React.memo(LogoDropdownInputComponent);
