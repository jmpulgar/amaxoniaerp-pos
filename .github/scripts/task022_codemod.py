from pathlib import Path

ROOT = Path("amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp")


def replace_literal(path: Path, literal: str, constant: str, expected: int) -> None:
    text = path.read_text()
    old = f'"{literal}"'
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"Expected {expected} matches in {path}, found {count}: {literal!r}")
    path.write_text(text.replace(old, constant))


def add_constants(path: Path, constants: str) -> None:
    text = path.read_text()
    lines = text.splitlines()
    imports = [i for i, line in enumerate(lines) if line.startswith("import ")]
    if not imports:
        raise RuntimeError(f"No imports found in {path}")
    insert_at = max(imports) + 1
    lines[insert_at:insert_at] = ["", *constants.rstrip().splitlines()]
    path.write_text("\n".join(lines) + "\n")


def update(rel: str, replacements: list[tuple[str, str, int]], constants: str) -> None:
    path = ROOT / rel
    for literal, constant, expected in replacements:
        replace_literal(path, literal, constant, expected)
    add_constants(path, constants)


update(
    "features/items/route/ItemsRoutes.kt",
    [
        ("Falta country_code en token", "ERR_MISSING_COUNTRY", 13),
        ("Falta admin_db en token", "ERR_MISSING_ADMIN_DB", 13),
    ],
    '''private const val ERR_MISSING_COUNTRY = "Falta country_code en token"
private const val ERR_MISSING_ADMIN_DB = "Falta admin_db en token"''',
)

update(
    "features/promotions/route/PromotionsRoutes.kt",
    [
        ("Falta country_code en token", "ERR_MISSING_COUNTRY", 1),
        ("Falta admin_db en token", "ERR_MISSING_ADMIN_DB", 1),
    ],
    '''private const val ERR_MISSING_COUNTRY = "Falta country_code en token"
private const val ERR_MISSING_ADMIN_DB = "Falta admin_db en token"''',
)

update(
    "features/mesas/PedidoMesaRouting.kt",
    [
        ("No se pudieron listar los pedidos", "ERR_LIST_ORDERS", 2),
        ("La petición no trae items para agregar", "ERR_EMPTY_ITEMS", 1),
        ("No se pudieron crear los pedidos", "ERR_CREATE_ORDERS", 2),
        ("No se pudo enviar la comanda", "ERR_SEND_ORDER", 2),
        ("El identificador de pedido es inválido", "ERR_INVALID_ORDER_ID", 1),
        ("El pedido no existe o no pertenece a la sesión", "ERR_ORDER_SCOPE", 1),
        ("No se pudo cambiar el estado del pedido", "ERR_UPDATE_ORDER_STATUS", 2),
    ],
    '''private const val ERR_LIST_ORDERS = "No se pudieron listar los pedidos"
private const val ERR_EMPTY_ITEMS = "La petición no trae items para agregar"
private const val ERR_CREATE_ORDERS = "No se pudieron crear los pedidos"
private const val ERR_SEND_ORDER = "No se pudo enviar la comanda"
private const val ERR_INVALID_ORDER_ID = "El identificador de pedido es inválido"
private const val ERR_ORDER_SCOPE = "El pedido no existe o no pertenece a la sesión"
private const val ERR_UPDATE_ORDER_STATUS = "No se pudo cambiar el estado del pedido"''',
)

update(
    "features/mesas/CuentaMesaRouting.kt",
    [
        ("La sesión no pertenece a esa mesa", "ERR_SESSION_SCOPE", 5),
        ("Respuesta inesperada", "ERR_UNEXPECTED", 2),
        ("No se pudieron listar las cuentas", "ERR_LIST_ACCOUNTS", 1),
        ("La sesión no admite cuentas (estado final)", "ERR_ACCOUNT_FINAL_STATE", 1),
        (
            "Un pedido seleccionado no existe, no está entregado o ya no tiene saldo",
            "ERR_SELECTED_ORDER_BALANCE",
            1,
        ),
        ("No se pudo crear la cuenta", "ERR_CREATE_ACCOUNT", 2),
        ("No se pudo obtener la cuenta", "ERR_GET_ACCOUNT", 1),
        ("No se pudo cancelar la cuenta", "ERR_CANCEL_ACCOUNT", 2),
    ],
    '''private const val ERR_SESSION_SCOPE = "La sesión no pertenece a esa mesa"
private const val ERR_UNEXPECTED = "Respuesta inesperada"
private const val ERR_LIST_ACCOUNTS = "No se pudieron listar las cuentas"
private const val ERR_ACCOUNT_FINAL_STATE = "La sesión no admite cuentas (estado final)"
private const val ERR_SELECTED_ORDER_BALANCE = "Un pedido seleccionado no existe, no está entregado o ya no tiene saldo"
private const val ERR_CREATE_ACCOUNT = "No se pudo crear la cuenta"
private const val ERR_GET_ACCOUNT = "No se pudo obtener la cuenta"
private const val ERR_CANCEL_ACCOUNT = "No se pudo cancelar la cuenta"''',
)

update(
    "features/mesas/SesionMesaRouting.kt",
    [
        ("Respuesta inesperada", "ERR_UNEXPECTED", 1),
        ("La mesa ya tiene una sesión activa", "ERR_SESSION_ALREADY_OPEN", 1),
        ("Mesa no encontrada en el área", "ERR_TABLE_AREA", 1),
        ("La mesa no está activa", "ERR_TABLE_INACTIVE", 1),
        ("Sesión no encontrada", "ERR_SESSION_NOT_FOUND", 1),
        ("La sesión ya no está abierta", "ERR_SESSION_CLOSED", 1),
    ],
    '''private const val ERR_UNEXPECTED = "Respuesta inesperada"
private const val ERR_SESSION_ALREADY_OPEN = "La mesa ya tiene una sesión activa"
private const val ERR_TABLE_AREA = "Mesa no encontrada en el área"
private const val ERR_TABLE_INACTIVE = "La mesa no está activa"
private const val ERR_SESSION_NOT_FOUND = "Sesión no encontrada"
private const val ERR_SESSION_CLOSED = "La sesión ya no está abierta"''',
)

update(
    "features/caja/CajaRouting.kt",
    [
        ("No se pudo consultar la secuencia", "ERR_QUERY_SEQUENCE", 1),
        ("No se pudo calcular secuencia", "ERR_CALCULATE_SEQUENCE", 1),
    ],
    '''private const val ERR_QUERY_SEQUENCE = "No se pudo consultar la secuencia"
private const val ERR_CALCULATE_SEQUENCE = "No se pudo calcular secuencia"''',
)

update(
    "features/sales/route/SalesRoutes.kt",
    [("Error interno al procesar venta", "ERR_PROCESS_SALE", 1)],
    'private const val ERR_PROCESS_SALE = "Error interno al procesar venta"',
)

print("extracted route response messages to compile-time constants; response text is unchanged")
