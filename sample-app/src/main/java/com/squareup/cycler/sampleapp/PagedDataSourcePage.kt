package com.squareup.cycler.sampleapp

import android.content.Context
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.cycler.DataSource
import com.squareup.cycler.Recycler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

@InternalCoroutinesApi
class PagedDataSourcePage(val context: Context, val scope: CoroutineScope) : Page {
    lateinit var recycler: Recycler<Int>

    // state
    private var allFlows: MutableList<Flow<PagedData>> = mutableListOf()
    var loadedItemsCount = 0
    var latestPagToken: Int? = null

    override fun toString() = "Paged Data Source"
    override val options: List<Pair<String, (Boolean) -> Unit>>
        get() = listOf()

    override fun config(recyclerView: RecyclerView) {
        recycler = Recycler.adopt(recyclerView) {
            row<Int, ItemView> {
                create { context ->
                    view = ItemView(context, showDragHandle = false)
                    bind { i ->
                        view.dispText(i.toString())
                    }
                }
            }
        }

        // set scroll listeners on recycler view to load data
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                if (lastVisibleItem == loadedItemsCount - 1) {
                    requestMoreItems()
                }
            }
        })
        addPage(flowOfData())
    }

    private fun addPage(page: Flow<PagedData>) {
        allFlows.add(page)
        scope.launch { // todo, fewer jobs??
            combine(allFlows) { pages -> // uhhhh...
                withContext(Dispatchers.Main) {
                    render(pages.flatMap { it.data }, pages.last().nextPageToken)
                }
            }.collect()
        }
    }


    private fun render(list: List<Int>, nextPagToken: Int?) {
        loadedItemsCount = list.size
        latestPagToken = nextPagToken
        recycler.update {
            data = object : DataSource<Int> {
                override val size: Int
                    get() = loadedItemsCount

                override fun get(i: Int): Int {
                    return list[i]
                }

            }
        }
    }

    fun requestMoreItems() {
        Toast.makeText(context, "requesting more items", Toast.LENGTH_SHORT).show()
        latestPagToken?.let {
            addPage(flowOfData(it))
        }
    }

    // data
    data class PagedData(val data: List<Int>, val previousPageToken: Int? = null, val nextPageToken: Int? = null)

    private fun flowOfData(pagToken: Int = 0): Flow<PagedData> {
        return flow {
            delay(300) // synthetic pause before data load
            emit(PagedData(data = ((pagToken * PAGE_SIZE) until (pagToken * PAGE_SIZE) + PAGE_SIZE).toList(),
                    previousPageToken = if (pagToken == 0) null else pagToken - 1,
                    nextPageToken = if (pagToken == MAX_PAGES) null else pagToken + 1
            ))
        }
    }

    companion object {
        const val PAGE_SIZE = 15
        const val MAX_PAGES = 10 // what if unknown
    }
}