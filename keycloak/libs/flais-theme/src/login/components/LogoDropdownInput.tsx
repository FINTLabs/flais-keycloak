import React, {
  type ChangeEvent,
  type HTMLAttributes,
  type KeyboardEvent,
  type MouseEvent,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { Chevron } from "./icons/Chevron";

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
}

export const LogoDropdownInput = React.memo(function LogoDropdownInput({
  id,
  name,
  options,
  value,
  onChange,
  placeholder,
  className,
  ...rest
}: LogoDropdownInputProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [isFiltering, setIsFiltering] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);

  const containerRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const listboxRef = useRef<HTMLUListElement>(null);
  const wasOpenRef = useRef(false);
  const selectedQueryRef = useRef("");

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

  const openList = (filtering = false) => {
    setIsFiltering(filtering);
    setOpen(true);
  };

  const closeList = (resetQuery = true) => {
    setOpen(false);
    setActiveIndex(-1);
    setIsFiltering(false);

    if (!resetQuery) return;

    const selectedLabel = selected?.label ?? "";
    setQuery(selectedLabel);
    selectedQueryRef.current = selectedLabel;
  };

  const selectOption = (option: LogoOption) => {
    onChange(option.id);
    setQuery(option.label);
    selectedQueryRef.current = option.label;
    setIsFiltering(false);
    closeList(false);
  };

  const handleInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const inputValue = event.target.value;
    const selectedLabel = selectedQueryRef.current;
    const shouldRemoveSelectedLabel =
      selected &&
      !isFiltering &&
      selectedLabel &&
      inputValue.startsWith(selectedLabel);

    setIsFiltering(true);
    setQuery(
      shouldRemoveSelectedLabel
        ? inputValue.slice(selectedLabel.length)
        : inputValue,
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
    if (isFiltering) return;

    const selectedLabel = selected?.label ?? "";
    setQuery(selectedLabel);
    selectedQueryRef.current = selectedLabel;
  }, [selected, isFiltering]);

  useEffect(() => {
    const handlePointerDown = (event: globalThis.MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) {
        closeList();
      }
    };

    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, [selected]);

  useEffect(() => {
    if (open && !wasOpenRef.current) {
      const selectedIndex = filteredOptions.findIndex(
        (option) => option.id === value,
      );
      setActiveIndex(
        selectedIndex >= 0
          ? selectedIndex
          : filteredOptions.length > 0
            ? 0
            : -1,
      );
    }

    wasOpenRef.current = open;
  }, [open, filteredOptions, value]);

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
      <input type="hidden" name={name} value={value ?? ""} />

      <div
        onMouseDown={(event: MouseEvent<HTMLDivElement>) => {
          event.preventDefault();
          openList();
          inputRef.current?.focus();
        }}
        className="
          flex min-h-12 w-full cursor-text items-center gap-2 rounded-md border border-gray-300
          bg-white px-3 py-2 text-left text-base text-gray-700 hover:bg-gray-50
          focus-within:z-10 focus-within:border-primary focus-within:outline-none
          focus-within:ring-1 focus-within:ring-primary sm:min-h-14 sm:px-4
        "
      >
        {selected?.logosUrl && query === selected.label && !isFiltering && (
          <span className="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-full">
            <img
              src={selected.logosUrl}
              alt=""
              aria-hidden="true"
              className="max-h-full max-w-full object-contain"
            />
          </span>
        )}

        <input
          ref={inputRef}
          id={controlId}
          type="text"
          role="combobox"
          aria-controls={listboxId}
          aria-expanded={open}
          aria-haspopup="listbox"
          aria-autocomplete="list"
          aria-activedescendant={
            open && activeOption ? `${listboxId}-${activeOption.id}` : undefined
          }
          value={query}
          placeholder={placeholder}
          autoComplete="off"
          onFocus={() => openList()}
          onChange={handleInputChange}
          onKeyDown={handleInputKeyDown}
          className="
            min-w-0 flex-1 bg-transparent text-gray-700 placeholder:text-gray-400
            focus:outline-none
          "
        />

        <button
          type="button"
          tabIndex={-1}
          aria-label={open ? "Lukk liste" : "Åpne liste"}
          onMouseDown={(event: MouseEvent<HTMLButtonElement>) => {
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
            className="h-4 w-4 text-gray-500"
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
            absolute z-10 mt-1 max-h-50 w-full overflow-auto rounded-md bg-white
            shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none sm:max-h-56
          "
        >
          {filteredOptions.length === 0 ? (
            <li className="px-3 py-2.5 text-base text-gray-500 sm:px-4 sm:py-3">
              Ingen treff
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
                  onMouseDown={(event: MouseEvent<HTMLLIElement>) => {
                    event.preventDefault();
                    selectOption(option);
                  }}
                  className={`
                    flex w-full cursor-pointer items-center px-3 py-2.5 text-left text-base
                    focus:outline-none sm:px-4 sm:py-3
                    ${
                      isSelected
                        ? "bg-gray-100 font-semibold text-gray-900"
                        : "text-gray-700 hover:bg-gray-50"
                    }
                    ${isActive && !isSelected ? "bg-gray-50" : ""}
                  `}
                >
                  {option.logosUrl && (
                    <span className="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded-full">
                      <img
                        src={option.logosUrl}
                        alt=""
                        aria-hidden="true"
                        className="max-h-full max-w-full object-contain"
                      />
                    </span>
                  )}

                  <span className="min-w-0 ml-2 flex-1 truncate">
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
});
