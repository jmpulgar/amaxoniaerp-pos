from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_exact(path: Path, old: str, new: str, expected: int) -> None:
    text = path.read_text(encoding="utf-8")
    actual = text.count(old)
    if actual != expected:
        raise RuntimeError(f"{path}: expected {expected}, found {actual}: {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


models = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/electronicinvoice/domain/ElectronicInvoiceModels.kt"
replace_exact(models, "val api_thefactoryhka: String,", "val apiTheFactoryHka: String,", 1)

processor = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/electronicinvoice/application/PanamaInvoiceProcessor.kt"
replace_exact(processor, ".api_thefactoryhka", ".apiTheFactoryHka", 5)

repository = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/electronicinvoice/data/ElectronicInvoiceRepository.kt"
replace_exact(repository, "config.api_thefactoryhka", "config.apiTheFactoryHka", 1)
replace_exact(repository, "api_thefactoryhka = apiTheFactoryHka.trimEnd('/')", "apiTheFactoryHka = apiTheFactoryHka.trimEnd('/')", 1)

credit_note = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/creditnotes/application/PanamaCreditNoteProcessor.kt"
replace_exact(credit_note, ".api_thefactoryhka", ".apiTheFactoryHka", 3)

payload_test = ROOT / "amaxoniaerp-backend/src/test/kotlin/com/amaxoniaerp/features/electronicinvoice/pac/thefactory/TheFactoryHkaPayloadBuilderTest.kt"
replace_exact(payload_test, "api_thefactoryhka =", "apiTheFactoryHka =", 1)

credit_note_test = ROOT / "amaxoniaerp-backend/src/test/kotlin/com/amaxoniaerp/features/electronicinvoice/pac/thefactory/TheFactoryHkaCreditNotePayloadBuilderTest.kt"
replace_exact(credit_note_test, "api_thefactoryhka =", "apiTheFactoryHka =", 1)
