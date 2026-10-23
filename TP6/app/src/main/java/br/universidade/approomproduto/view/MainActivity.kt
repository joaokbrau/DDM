package br.universidade.approomproduto.view

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import br.universidade.approomproduto.dao.ProductDao
import br.universidade.approomproduto.databinding.ActivityMainBinding
import br.universidade.approomproduto.db.AppDatabase
import br.universidade.approomproduto.model.Product
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var productDao: ProductDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        )
            .fallbackToDestructiveMigration() // recria o banco se o esquema mudar (apaga dados!)
            .build()
        productDao = db.productDao()

        configurarInserir()
        configurarListar()
        configurarBuscar()
        configurarAtualizarPreco()
        configurarExcluirPorId()
        configurarExcluirTodos()
    }

    // ---------------------------------------------------------------
    // Inserir (A1 limpar campos, A2 validação de preço, A8 quantidade)
    // ---------------------------------------------------------------
    private fun configurarInserir() {
        binding.btnInserirProduto.setOnClickListener {
            val name = binding.editProductName.text.toString().trim()
            val priceText = binding.editProductPrice.text.toString().trim()
            val description = binding.editProductDescription.text.toString().trim()
            val quantity = binding.editProductQuantity.text.toString().trim()
                .toIntOrNull() ?: 0

            if (name.isEmpty()) {
                binding.editProductName.error = "Informe o nome"
                return@setOnClickListener
            }

            // A2: preço vazio, zero ou negativo não é aceito
            val price = priceText.toDoubleOrNull()
            if (price == null || price <= 0.0) {
                binding.editProductPrice.error = "Informe um preço válido"
                return@setOnClickListener
            }

            val product = Product(
                name = name,
                price = price,
                description = description,
                quantity = quantity
            )

            lifecycleScope.launch {
                productDao.insertAll(product)
                Log.i("TAG", "Produto inserido: $name - R$$price")
                Toast.makeText(this@MainActivity, "Produto inserido!", Toast.LENGTH_SHORT).show()

                // A1: limpa os campos e devolve o cursor ao campo Nome
                binding.editProductName.text.clear()
                binding.editProductPrice.text.clear()
                binding.editProductDescription.text.clear()
                binding.editProductQuantity.text.clear()
                binding.editProductName.requestFocus()
            }
        }
    }

    // ---------------------------------------------------------------
    // Listar (A3 lista na tela, A4 total do estoque)
    // ---------------------------------------------------------------
    private fun configurarListar() {
        binding.btnListarProdutos.setOnClickListener {
            lifecycleScope.launch {
                val products = productDao.getAll()
                products.forEach {
                    Log.i(
                        "TAG",
                        "Produto = ${it.name}, Preço = R$${it.price}, " +
                            "Descrição = ${it.description}, Qtd = ${it.quantity}"
                    )
                }
                if (products.isEmpty()) Log.i("TAG", "Nenhum produto encontrado.")
                mostrarNaTela(products)
            }
        }
    }

    // ---------------------------------------------------------------
    // Buscar por nome (A5)
    // ---------------------------------------------------------------
    private fun configurarBuscar() {
        binding.btnBuscar.setOnClickListener {
            val texto = binding.editBusca.text.toString().trim()
            lifecycleScope.launch {
                val products = productDao.buscarPorNome("%$texto%")
                mostrarNaTela(products)
            }
        }
    }

    // ---------------------------------------------------------------
    // Atualizar preço (A7)
    // ---------------------------------------------------------------
    private fun configurarAtualizarPreco() {
        binding.btnAtualizarPreco.setOnClickListener {
            val id = binding.editProductId.text.toString().trim().toIntOrNull()
            if (id == null) {
                binding.editProductId.error = "Informe o ID"
                return@setOnClickListener
            }

            val novoPreco = binding.editNovoPreco.text.toString().trim().toDoubleOrNull()
            if (novoPreco == null || novoPreco <= 0.0) {
                binding.editNovoPreco.error = "Informe um preço válido"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val produto = productDao.getById(id)
                if (produto == null) {
                    Toast.makeText(this@MainActivity, "Produto não encontrado", Toast.LENGTH_SHORT).show()
                } else {
                    productDao.update(produto.copy(price = novoPreco))
                    Toast.makeText(this@MainActivity, "Preço atualizado!", Toast.LENGTH_SHORT).show()
                    binding.editNovoPreco.text.clear()
                    mostrarNaTela(productDao.getAll())
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Excluir por id (A6 desafio, usando @Delete)
    // ---------------------------------------------------------------
    private fun configurarExcluirPorId() {
        binding.btnExcluirPorId.setOnClickListener {
            val id = binding.editProductId.text.toString().trim().toIntOrNull()
            if (id == null) {
                binding.editProductId.error = "Informe o ID"
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val produto = productDao.getById(id)
                if (produto == null) {
                    Toast.makeText(this@MainActivity, "Produto não encontrado", Toast.LENGTH_SHORT).show()
                } else {
                    productDao.delete(produto)
                    Toast.makeText(this@MainActivity, "Produto excluído!", Toast.LENGTH_SHORT).show()
                    binding.editProductId.text.clear()
                    mostrarNaTela(productDao.getAll())
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Excluir todos (A6)
    // ---------------------------------------------------------------
    private fun configurarExcluirTodos() {
        binding.btnExcluirTodos.setOnClickListener {
            lifecycleScope.launch {
                productDao.deleteAll()
                Toast.makeText(this@MainActivity, "Todos os produtos foram excluídos", Toast.LENGTH_SHORT).show()
                mostrarNaTela(emptyList())
            }
        }
    }

    // ---------------------------------------------------------------
    // Mostra a lista + total de produtos + soma dos preços (A3 e A4)
    // ---------------------------------------------------------------
    private fun mostrarNaTela(products: List<Product>) {
        if (products.isEmpty()) {
            binding.textResultado.text = "Nenhum produto encontrado."
            return
        }

        val linhas = products.joinToString("\n") {
            "ID ${it.id} | ${it.name} | ${formatarPreco(it.price)} | " +
                "Qtd: ${it.quantity} | ${it.description.orEmpty()}"
        }

        val total = products.sumOf { it.price }
        binding.textResultado.text = buildString {
            append(linhas)
            append("\n\n")
            append("Produtos cadastrados: ${products.size}\n")
            append("Soma dos preços: ${formatarPreco(total)}")
        }
    }

    private fun formatarPreco(valor: Double): String =
        "R$ " + String.format(Locale("pt", "BR"), "%.2f", valor)
}
