import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";
import { PlatformModal } from "./PlatformModal.jsx";

function Example() {
  const [open, setOpen] = useState(false);
  return <><button onClick={() => setOpen(true)}>Abrir</button>{open && <PlatformModal title="Confirmação" description="Detalhes da ação" onClose={() => setOpen(false)}><button>Cancelar</button><button>Confirmar</button></PlatformModal>}</>;
}

describe("platform dialog accessibility", () => {
  it("labels the dialog, traps keyboard focus and restores focus on Escape", async () => {
    const user = userEvent.setup();
    render(<Example />);
    const trigger = screen.getByRole("button", { name: "Abrir" });
    await user.click(trigger);
    const dialog = screen.getByRole("dialog", { name: "Confirmação" });
    expect(dialog).toHaveAttribute("aria-modal", "true");
    expect(dialog).toHaveAccessibleDescription("Detalhes da ação");
    expect(dialog).toHaveFocus();
    await user.tab();
    expect(within(dialog).getByRole("button", { name: "Fechar" })).toHaveFocus();
    await user.tab({ shift: true });
    expect(within(dialog).getByRole("button", { name: "Confirmar" })).toHaveFocus();
    await user.tab();
    expect(within(dialog).getByRole("button", { name: "Fechar" })).toHaveFocus();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
    expect(document.body.style.overflow).not.toBe("hidden");
  });

  it("dismisses only backdrop clicks, preserving clicks inside the dialog", () => {
    render(<Example />);
    fireEvent.click(screen.getByRole("button", { name: "Abrir" }));
    fireEvent.click(screen.getByText("Detalhes da ação"));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("presentation"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
