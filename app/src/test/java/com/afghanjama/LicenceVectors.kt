package com.afghanjama

/**
 * نمونه‌های آزمونِ پروتکلِ LNM1 — برداشته از
 * `Linumic/licensing/test-vectors.json`.
 *
 * **فقط کلیدِ عمومیِ آزمایشی اینجاست.** کلیدِ خصوصیِ آزمایشیِ همان فایل
 * عمداً کپی نشد: آزمون فقط *سنجیدن* لازم دارد، نه ساختن. و هیچ‌کدام از
 * این‌ها به کدِ ارسالی راه ندارد؛ آزمون‌ها کلید را به `Lnm1.verify` و
 * `Licensing` **تزریق** می‌کنند.
 *
 * اگر فایلِ نمونه‌ها عوض شد، این فایل از نو ساخته شود — دستی ویرایش نشود.
 */
object LicenceVectors {

    data class Vector(val case: String, val product: String, val machine: String, val key: String)

    const val TEST_PUBLIC_PEM = "-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE67E5DnyHMQ/q+n5x0/aT89n0K+QK\nKaCAJDn/AoDbNzp+rUzlXF+rR2oKfKVrEIWoQ6dST85z6Sn4ydl3PEP4Kw==\n-----END PUBLIC KEY-----\n"
    val MACHINE_CODES = listOf(
        Triple("mediflow", "test-device-1", "4HDG-0NK5-EAKX-GQVR"),
        Triple("mediflow", "3F2504E0-4F89-11D3-9A0C-0305E82C3301", "QRK7-PK6P-V2WY-XYXM"),
        Triple("mediflow", "9774d56d682e549c", "JYJP-RA22-2MPB-P8N9"),
        Triple("khayatyar", "test-device-1", "BDZ2-PWKS-XGAW-3YGB"),
        Triple("khayatyar", "3F2504E0-4F89-11D3-9A0C-0305E82C3301", "Y5XX-H0AY-2634-X0AQ"),
        Triple("khayatyar", "9774d56d682e549c", "9CRF-0ZJX-FWKR-XB6T"),
    )
    val KEYS = listOf(
        Vector("valid_perpetual", "mediflow", "4HDG-0NK5-EAKX-GQVR", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdin24zYtNuMIC8gVGVzdCIsImUiOm51bGwsImVkIjoic3RhbmRhcmQiLCJmIjpbIioiXSwiaSI6IjIwMjYtMTAtMDgiLCJpZCI6Ik1GLVRFU1QtMDAwMSIsIm0iOiI0SERHLTBOSzUtRUFLWC1HUVZSIiwicCI6Im1lZGlmbG93IiwidiI6MX0.MEYCIQCpP8KjxPPLU3Mig16O24TallVQuaR77JXnpEdDv9cCCAIhAKMUd19SX8yeDy_ZG7D3fy2DN_mHAyb-FisDo31RT4z8"),
        Vector("valid_expires_2026_12_31", "mediflow", "4HDG-0NK5-EAKX-GQVR", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdin24zYtNuMIC8gVGVzdCIsImUiOiIyMDI2LTEyLTMxIiwiZWQiOiJzdGFuZGFyZCIsImYiOlsiKiJdLCJpIjoiMjAyNi0xMC0wOCIsImlkIjoiTUYtVEVTVC0wMDAxIiwibSI6IjRIREctME5LNS1FQUtYLUdRVlIiLCJwIjoibWVkaWZsb3ciLCJ2IjoxfQ.MEYCIQCls8ZkdWfIZEaOnCI2aRhCtXWmSWOCzcbpR46SGs7cBgIhAOq4sB_xYEcEXl_-RKYdCJIcH5RTNU0M5K0udrpXTnHz"),
        Vector("valid_any_machine", "mediflow", "*", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdin24zYtNuMIC8gVGVzdCIsImUiOm51bGwsImVkIjoic3RhbmRhcmQiLCJmIjpbIioiXSwiaSI6IjIwMjYtMTAtMDgiLCJpZCI6Ik1GLVRFU1QtMDAwMSIsIm0iOiIqIiwicCI6Im1lZGlmbG93IiwidiI6MX0.MEYCIQDZDA8i21lQa1p9Rb9eTKjCcqU49QJi36uHwzJnJgFR2QIhAMPJGPbbZAyOUQi0gxeMibx9diXi0Sshz53L3vnQZO86"),
        Vector("wrong_product", "mediflow", "", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdin24zYtNuMIC8gVGVzdCIsImUiOm51bGwsImVkIjoic3RhbmRhcmQiLCJmIjpbIioiXSwiaSI6IjIwMjYtMTAtMDgiLCJpZCI6Ik1GLVRFU1QtMDAwMSIsIm0iOiI0SERHLTBOSzUtRUFLWC1HUVZSIiwicCI6ImtoYXlhdHlhciIsInYiOjF9.MEQCIAJ4dIPpbihdrPHp_GPNPh1gka9-22JrhGZrSKjXaYxkAiBnKyWldyFj640WbKZ0gEkLJfNygIB9bJeSgdHZ7TcLNA"),
        Vector("tampered_payload", "mediflow", "", "LNM1.eyJjIjoiVGFtcGVyZWQiLCJlIjpudWxsLCJlZCI6InN0YW5kYXJkIiwiZiI6WyIqIl0sImkiOiIyMDI2LTEwLTA4IiwiaWQiOiJNRi1URVNULTAwMDEiLCJtIjoiNEhERy0wTks1LUVBS1gtR1FWUiIsInAiOiJtZWRpZmxvdyIsInYiOjF9.MEUCIEbRohvIiQWVVvCqIW3hIhATMW4cDZ3KBYaMKLuNsIlsAiEAh7E3MFS-rugncQEeBCBH1hUIlbsPqL2WBLwqLSz_vLc"),
        Vector("wrong_version", "mediflow", "", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdin24zYtNuMIC8gVGVzdCIsImUiOm51bGwsImVkIjoic3RhbmRhcmQiLCJmIjpbIioiXSwiaSI6IjIwMjYtMTAtMDgiLCJpZCI6Ik1GLVRFU1QtMDAwMSIsIm0iOiI0SERHLTBOSzUtRUFLWC1HUVZSIiwicCI6Im1lZGlmbG93IiwidiI6Mn0.MEYCIQC8aGmJ59LCHwwCtGU5eKZr4e1jqD6v9CtC5kIFnoAqpQIhAPXSOgcULsZ-V2Yuu2Zwd6pZNWYACj9AzB9zEa_AQaIl"),
        Vector("with_whitespace_valid", "mediflow", "", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdi\n  n24zYtNuMIC8gVGVzdCIsImUiOm51bGwsImVkIjoic3RhbmRhcmQiLCJmIjpbIioiXSwiaSI6IjIwMjY tMTAtMDgiLCJpZCI6Ik1GLVRFU1QtMDAwMSIsIm0iOiI0SERHLTBOSzUtRUFLWC1HUVZSIiwicCI6Im1lZGlmbG93IiwidiI6MX0.MEUCIEbRohvIiQWVVvCqIW3hIhATMW4cDZ3KBYaMKLuNsIlsAiEAh7E3MFS-rugncQEeBCBH1hUIlbsPqL2WBLwqLSz_vLc"),
        Vector("valid_perpetual", "khayatyar", "BDZ2-PWKS-XGAW-3YGB", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdin24zYtNuMIC8gVGVzdCIsImUiOm51bGwsImVkIjoic3RhbmRhcmQiLCJmIjpbIioiXSwiaSI6IjIwMjYtMTAtMDgiLCJpZCI6IktZLVRFU1QtMDAwMSIsIm0iOiJCRFoyLVBXS1MtWEdBVy0zWUdCIiwicCI6ImtoYXlhdHlhciIsInYiOjF9.MEUCIQDffJ6NaqAvbr_6ggiy0Qc2Wv7_CFy5j1ISbKX3VPEZCAIgYefrlVPyWpcMfKg1E5RQYkFBBanlGINoG_-IZ6O51IQ"),
        Vector("valid_expires_2026_12_31", "khayatyar", "BDZ2-PWKS-XGAW-3YGB", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdin24zYtNuMIC8gVGVzdCIsImUiOiIyMDI2LTEyLTMxIiwiZWQiOiJzdGFuZGFyZCIsImYiOlsiKiJdLCJpIjoiMjAyNi0xMC0wOCIsImlkIjoiS1ktVEVTVC0wMDAxIiwibSI6IkJEWjItUFdLUy1YR0FXLTNZR0IiLCJwIjoia2hheWF0eWFyIiwidiI6MX0.MEQCIDBOKUMPeYspacsNmyuBlyNvipjWkpeAZckuW8btEYaUAiAPPfQl4DbxLk9WRUZywsuP5veQCcfg4lFdDLuqBDLhqg"),
        Vector("valid_any_machine", "khayatyar", "*", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdin24zYtNuMIC8gVGVzdCIsImUiOm51bGwsImVkIjoic3RhbmRhcmQiLCJmIjpbIioiXSwiaSI6IjIwMjYtMTAtMDgiLCJpZCI6IktZLVRFU1QtMDAwMSIsIm0iOiIqIiwicCI6ImtoYXlhdHlhciIsInYiOjF9.MEYCIQDyH6HEs4ezonL18P80IO6YjBeWAkJ5RMLK-aICAb3nIAIhAP5PZ_mOTinoOrFIU6g2ODxBuXRgRFKYc_EXYNRM8508"),
        Vector("wrong_product", "khayatyar", "", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdin24zYtNuMIC8gVGVzdCIsImUiOm51bGwsImVkIjoic3RhbmRhcmQiLCJmIjpbIioiXSwiaSI6IjIwMjYtMTAtMDgiLCJpZCI6IktZLVRFU1QtMDAwMSIsIm0iOiJCRFoyLVBXS1MtWEdBVy0zWUdCIiwicCI6Im1lZGlmbG93IiwidiI6MX0.MEUCIQDUpR2qg5x90fJ8zfCjPKmSCJ2Fsylpj49o-WkamtwMyQIgIn1fGI4yC-6eO1Onn2_4NX4pWQNbS-j-EYrxYYYdJeE"),
        Vector("tampered_payload", "khayatyar", "", "LNM1.eyJjIjoiVGFtcGVyZWQiLCJlIjpudWxsLCJlZCI6InN0YW5kYXJkIiwiZiI6WyIqIl0sImkiOiIyMDI2LTEwLTA4IiwiaWQiOiJLWS1URVNULTAwMDEiLCJtIjoiQkRaMi1QV0tTLVhHQVctM1lHQiIsInAiOiJraGF5YXR5YXIiLCJ2IjoxfQ.MEUCIQDu7wuT5SuZI4xBWP5GTIePB4sUdaZcyAwL_bsv0NTe-gIgWLQpfS2A-GpVdhP6PsOz9qzyB-kUb3D8AL_JJbzEB9g"),
        Vector("wrong_version", "khayatyar", "", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdin24zYtNuMIC8gVGVzdCIsImUiOm51bGwsImVkIjoic3RhbmRhcmQiLCJmIjpbIioiXSwiaSI6IjIwMjYtMTAtMDgiLCJpZCI6IktZLVRFU1QtMDAwMSIsIm0iOiJCRFoyLVBXS1MtWEdBVy0zWUdCIiwicCI6ImtoYXlhdHlhciIsInYiOjJ9.MEQCIE3yYjrOylkAxjzSxcu9nOAjM2k1VgOrd_DlhB0hvi8RAiBCQ9YhyBel_EZ4ZAmWUDqlSXwNjrsPZKcRVtXFFPqAew"),
        Vector("with_whitespace_valid", "khayatyar", "", "LNM1.eyJjIjoi2qnZhNuM2YbbjNqpINii2LLZhdi\n  n24zYtNuMIC8gVGVzdCIsImUiOm51bGwsImVkIjoic3RhbmRhcmQiLCJmIjpbIioiXSwiaSI6IjIwMjY tMTAtMDgiLCJpZCI6IktZLVRFU1QtMDAwMSIsIm0iOiJCRFoyLVBXS1MtWEdBVy0zWUdCIiwicCI6ImtoYXlhdHlhciIsInYiOjF9.MEUCIQDu7wuT5SuZI4xBWP5GTIePB4sUdaZcyAwL_bsv0NTe-gIgWLQpfS2A-GpVdhP6PsOz9qzyB-kUb3D8AL_JJbzEB9g"),
    )

    fun key(product: String, case: String): String =
        KEYS.single { it.product == product && it.case == case }.key
}
