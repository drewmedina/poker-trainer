interface Option<T extends string | number> {
  value: T;
  label: string;
}

interface Props<T extends string | number> {
  options: Option<T>[];
  value: T;
  onChange: (value: T) => void;
  label: string;
  large?: boolean;
  mono?: boolean;
}

export function Segmented<T extends string | number>({ options, value, onChange, label, large, mono }: Props<T>) {
  return (
    <div className={`segmented${large ? ' large' : ''}`} role="group" aria-label={label}>
      {options.map((o) => (
        <button
          key={String(o.value)}
          type="button"
          aria-pressed={o.value === value}
          className={mono ? 'mono' : undefined}
          onClick={() => onChange(o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}
