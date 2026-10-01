export function NotFoundState({ Icon, title, description, detail }) {
  return (
    <main className="not-found-page">
      <div className="not-found-content">
        <span className="not-found-icon" aria-hidden="true">
          <Icon size={36} strokeWidth={1.8} />
        </span>
        <h1>{title}</h1>
        <div className="not-found-copy">
          <p>{description}</p>
          <p>{detail}</p>
        </div>
      </div>
    </main>
  );
}
