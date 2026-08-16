from pathlib import Path

ROOT = Path("amaxoniaerp-backend")


def replace_exact(path: Path, old: str, new: str, expected: int) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"Expected {expected} matches in {path}, found {count}: {old!r}")
    path.write_text(text.replace(old, new))


models = ROOT / "src/main/kotlin/com/amaxoniaerp/features/electronicinvoice/domain/ElectronicInvoiceModels.kt"
replace_exact(models, "    val api_thefactoryhka: String,", "    val apiTheFactoryHka: String,", 1)

credit_processor = ROOT / "src/main/kotlin/com/amaxoniaerp/features/creditnotes/application/PanamaCreditNoteProcessor.kt"
replace_exact(credit_processor, ".api_thefactoryhka", ".apiTheFactoryHka", 3)

invoice_processor = ROOT / "src/main/kotlin/com/amaxoniaerp/features/electronicinvoice/application/PanamaInvoiceProcessor.kt"
replace_exact(invoice_processor, ".api_thefactoryhka", ".apiTheFactoryHka", 5)

repository = ROOT / "src/main/kotlin/com/amaxoniaerp/features/electronicinvoice/data/ElectronicInvoiceRepository.kt"
replace_exact(repository, "config.api_thefactoryhka", "config.apiTheFactoryHka", 1)
replace_exact(repository, "            api_thefactoryhka = apiTheFactoryHka.trimEnd('/'),", "            apiTheFactoryHka = apiTheFactoryHka.trimEnd('/'),", 1)

credit_test = ROOT / "src/test/kotlin/com/amaxoniaerp/features/electronicinvoice/pac/thefactory/TheFactoryHkaCreditNotePayloadBuilderTest.kt"
replace_exact(credit_test, "                            api_thefactoryhka = \"https://example.com\",", "                            apiTheFactoryHka = \"https://example.com\",", 1)

payload_test = ROOT / "src/test/kotlin/com/amaxoniaerp/features/electronicinvoice/pac/thefactory/TheFactoryHkaPayloadBuilderTest.kt"
replace_exact(payload_test, "                api_thefactoryhka = \"https://example.com\",", "                apiTheFactoryHka = \"https://example.com\",", 1)

print("normalized FEConfigData property naming without changing DB column names or wire contracts")
