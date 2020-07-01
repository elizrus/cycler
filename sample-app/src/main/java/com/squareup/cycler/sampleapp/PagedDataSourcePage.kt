package com.squareup.cycler.sampleapp

import android.content.Context
import android.util.Log
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
    lateinit var recycler: Recycler<PageAwareInt>

    data class PageAwareInt(val nextPageToken: Int?, val previousPageToken: Int?, val data: Int)

    // state
    private var allFlows: MutableList<Flow<PagedData>> = mutableListOf()
    var isLoadingMoreItems = false

    override fun toString() = "Paged Data Source"
    override val options: List<Pair<String, (Boolean) -> Unit>>
        get() = listOf()

    override fun config(recyclerView: RecyclerView) {
        recycler = Recycler.adopt(recyclerView) {
            row<PageAwareInt, ItemView> {
                create { context ->
                    view = ItemView(context, showDragHandle = false)
                    bind { i ->
                        view.dispText(i.data.toString())
                    }
                }
            }
        }

        // set scroll listeners on recycler view to load data
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                // scroll down
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                if (lastVisibleItem == recycler.data.size - 1 && !isLoadingMoreItems) {
                    isLoadingMoreItems = true
                    requestMoreItems(LoadDirection.DOWN, recycler.data[lastVisibleItem].nextPageToken)
                } else {
                    // scroll up
                    val firstVisibleItem = layoutManager.findFirstVisibleItemPosition()
                    if (firstVisibleItem == 0 && !isLoadingMoreItems) {
                        isLoadingMoreItems = true
                        requestMoreItems(LoadDirection.UP, recycler.data[firstVisibleItem].previousPageToken)
                    }
                }
            }
        })

        // initialize with data
        addPage(LoadDirection.UP, flowOfData())
    }

    private fun addPage(loadDirection: LoadDirection, page: Flow<PagedData>) {
        when (loadDirection) {
            LoadDirection.DOWN -> {
                allFlows.add(page)
                if (allFlows.size > MAX_PAGES_VISIBLE) { // drop if large
                    allFlows.removeAt(0)
                }
            }
            LoadDirection.UP -> {
                allFlows.add(0, page)
                if (allFlows.size > MAX_PAGES_VISIBLE) { // drop if large
                    allFlows.dropLast(1)
                }
            }
        }
        scope.launch { // todo, fewer jobs??
            combine(allFlows) { pages -> // uhhhh...
                withContext(Dispatchers.Main) {
                    render(pages.flatMap { pagedData ->
                        pagedData.data.map {
                            PageAwareInt(
                                    nextPageToken = pagedData.nextPageToken,
                                    previousPageToken = pagedData.previousPageToken,
                                    data = it
                            )
                        }
                    })
                    isLoadingMoreItems = false
                }
            }.collect()
        }
    }

    private fun render(list: List<PageAwareInt>) {
        recycler.update {
            data = object : DataSource<PageAwareInt> {
                override val size: Int
                    get() = list.size

                override fun get(i: Int): PageAwareInt {
                    return list[i]
                }

            }
        }
    }

    enum class LoadDirection { UP, DOWN }

    fun requestMoreItems(loadDirection: LoadDirection, pagToken: Int?) {
        Log.i("Elizabeth","requesting page: $pagToken")
        pagToken?.let {
            addPage(loadDirection, flowOfData(it))
        } ?: run { isLoadingMoreItems = false }
    }

    // data
    data class PagedData(val data: List<Int>, val previousPageToken: Int? = null, val nextPageToken: Int? = null)

    private fun flowOfData(pagToken: Int = 0): Flow<PagedData> {
        return flow {
            delay(300) // synthetic pause before data load
            emit(PagedData(data = ((pagToken * PAGE_SIZE) until (pagToken * PAGE_SIZE) + PAGE_SIZE).toList(),
                    previousPageToken = if (pagToken == -MAX_PAGES) null else pagToken - 1,
                    nextPageToken = if (pagToken == MAX_PAGES) null else pagToken + 1
            ))
        }
    }

    companion object {
        const val PAGE_SIZE = 15
        const val MAX_PAGES = 100 // what if unknown
        const val MAX_PAGES_VISIBLE = 5
    }
}