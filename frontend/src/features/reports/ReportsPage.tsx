import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TablePagination from '@mui/material/TablePagination';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import DownloadOutlined from '@mui/icons-material/DownloadOutlined';
import PlayArrowOutlined from '@mui/icons-material/PlayArrowOutlined';
import { reportApi, type ReportFilters as Filters } from '@/api/insight';
import { toErrorMessage } from '@/api/axiosClient';
import { EmptyState } from '@/components/EmptyState';
import { PageHeader } from '@/components/PageHeader';
import { StatusChip } from '@/components/StatusChip';
import { TableSkeleton } from '@/components/LoadingState';
import { useAuth } from '@/contexts/AuthContext';
import type { ColumnType, ReportDefinition, ReportFormat, ReportRequestSummary } from '@/types/insight';
import { ReportFilters } from './ReportFilters';

const FORMATS: ReportFormat[] = ['XLSX', 'CSV', 'PDF'];

/**
 * One screen for every report.
 *
 * <p>The catalogue, the filters and the columns all come from the server, so a
 * report added on the backend appears here without a frontend change.</p>
 */
export function ReportsPage() {
  const { can } = useAuth();
  const queryClient = useQueryClient();

  const [selected, setSelected] = useState<ReportDefinition | null>(null);
  const [filters, setFilters] = useState<Filters>({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(25);
  const [ran, setRan] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [exportAnchor, setExportAnchor] = useState<HTMLElement | null>(null);

  const catalogue = useQuery({ queryKey: ['reports', 'catalogue'], queryFn: reportApi.catalogue });

  const exports = useQuery({
    queryKey: ['reports', 'exports'],
    queryFn: () => reportApi.exports(0, 5),
    enabled: can('REPORT_EXPORT'),
  });

  useEffect(() => {
    if (!selected && (catalogue.data ?? []).length > 0) {
      choose(catalogue.data![0]);
    }
  }, [catalogue.data, selected]);

  const result = useQuery({
    queryKey: ['reports', 'run', selected?.key, filters, page, size],
    queryFn: () => reportApi.run(selected!.key, filters, page, size),
    enabled: Boolean(selected) && ran,
    retry: false,
  });

  useEffect(() => {
    if (result.isError) {
      setError(toErrorMessage(result.error));
    }
  }, [result.isError, result.error]);

  const runExport = useMutation({
    mutationFn: (format: ReportFormat) => reportApi.export(selected!.key, format, filters),
    onSuccess: async (request) => {
      setError(null);
      void queryClient.invalidateQueries({ queryKey: ['reports', 'exports'] });
      if (request.status === 'READY') {
        await reportApi.download(request);
        setNotice(`${request.fileName} downloaded (${request.rowCount} rows).`);
      } else {
        setNotice('That export is large, so it is being written in the background. '
          + 'You will be notified when it is ready.');
      }
    },
    onError: (caught) => setError(toErrorMessage(caught)),
  });

  const grouped = useMemo(() => {
    const groups = new Map<string, ReportDefinition[]>();
    for (const definition of catalogue.data ?? []) {
      const list = groups.get(definition.group) ?? [];
      list.push(definition);
      groups.set(definition.group, list);
    }
    return [...groups.entries()];
  }, [catalogue.data]);

  function choose(definition: ReportDefinition) {
    setSelected(definition);
    setFilters(defaultsFor(definition));
    setPage(0);
    setRan(false);
    setError(null);
    setNotice(null);
  }

  const data = result.data;

  return (
    <Box>
      <PageHeader
        eyebrow="Reports"
        title="Reports"
        description="Fifteen reports over the same data your screens show, exported with the filters printed on them."
      />

      {error && (
        <Alert severity="error" sx={{ mb: 3 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      {notice && (
        <Alert severity="success" sx={{ mb: 3 }} onClose={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: 4, gridTemplateColumns: { xs: '1fr', lg: '260px 1fr' } }}>
        <Card sx={{ alignSelf: 'start' }}>
          <CardContent sx={{ p: 3 }}>
            {grouped.map(([group, definitions]) => (
              <Box key={group} sx={{ mb: 3 }}>
                <Typography variant="overline" sx={{ color: 'text.secondary' }}>
                  {group}
                </Typography>
                {definitions.map((definition) => (
                  <Box
                    key={definition.key}
                    onClick={() => choose(definition)}
                    sx={{
                      px: 2,
                      py: 1.25,
                      borderRadius: 1.5,
                      cursor: 'pointer',
                      bgcolor: selected?.key === definition.key ? 'action.selected' : undefined,
                      '&:hover': { bgcolor: 'action.hover' },
                    }}
                  >
                    <Typography
                      variant="body2"
                      sx={{ fontWeight: selected?.key === definition.key ? 600 : 400 }}
                    >
                      {definition.title}
                    </Typography>
                  </Box>
                ))}
              </Box>
            ))}

            {!catalogue.isLoading && grouped.length === 0 && (
              <Typography variant="body2" sx={{ color: 'text.secondary', p: 2 }}>
                No reports are available to you.
              </Typography>
            )}
          </CardContent>
        </Card>

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
          {selected && (
            <Card>
              <CardContent sx={{ p: 4 }}>
                <Typography variant="h5" sx={{ mb: 0.5 }}>
                  {selected.title}
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary', mb: 3 }}>
                  {selected.description}
                </Typography>

                <ReportFilters
                  filters={selected.filters}
                  values={filters}
                  onChange={(next) => {
                    setFilters(next);
                    setPage(0);
                  }}
                />

                <Box sx={{ display: 'flex', gap: 2, mt: 4, justifyContent: 'flex-end' }}>
                  <Button
                    variant="contained"
                    startIcon={<PlayArrowOutlined />}
                    onClick={() => {
                      setRan(true);
                      setError(null);
                      void result.refetch();
                    }}
                  >
                    Run report
                  </Button>
                  {can('REPORT_EXPORT') && (
                    <Button
                      variant="outlined"
                      startIcon={<DownloadOutlined />}
                      disabled={runExport.isPending}
                      onClick={(event) => setExportAnchor(event.currentTarget)}
                    >
                      Export
                    </Button>
                  )}
                </Box>
              </CardContent>
            </Card>
          )}

          {result.isFetching && <TableSkeleton rows={6} columns={6} />}

          {data && !result.isFetching && (
            <Card>
              <Box sx={{ p: 3, display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
                <Typography variant="subtitle2">{data.totalRows} rows</Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  generated {new Date(data.generatedAt).toLocaleTimeString('en-GB')}
                </Typography>
                {data.truncated && <Chip size="small" color="warning" label="Result capped" />}
              </Box>
              <Divider />

              {data.rows.length === 0 ? (
                <EmptyState
                  title="No rows matched"
                  description="Widen the date range, or clear a filter."
                />
              ) : (
                <Box sx={{ overflowX: 'auto' }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        {data.columns.map((column) => (
                          <TableCell
                            key={column.key}
                            align={numeric(column.type) ? 'right' : 'left'}
                            sx={{ whiteSpace: 'nowrap' }}
                          >
                            {column.label}
                          </TableCell>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {data.rows.map((row, rowIndex) => (
                        <TableRow key={rowIndex} hover>
                          {data.columns.map((column, index) => (
                            <TableCell
                              key={column.key}
                              align={numeric(column.type) ? 'right' : 'left'}
                              className={numeric(column.type) ? 'tabular' : undefined}
                              sx={{ whiteSpace: column.type === 'TEXT' ? 'normal' : 'nowrap' }}
                            >
                              {column.type === 'STATUS' && row[index] != null ? (
                                <StatusChip status={String(row[index])} />
                              ) : (
                                format(row[index], column.type)
                              )}
                            </TableCell>
                          ))}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )}

              <TablePagination
                component="div"
                count={data.totalRows}
                page={page}
                rowsPerPage={size}
                onPageChange={(_, next) => setPage(next)}
                onRowsPerPageChange={(event) => {
                  setSize(Number(event.target.value));
                  setPage(0);
                }}
                rowsPerPageOptions={[10, 25, 50, 100]}
              />
            </Card>
          )}

          {can('REPORT_EXPORT') && (exports.data?.content ?? []).length > 0 && (
            <Card>
              <CardContent sx={{ p: 4 }}>
                <Typography variant="h6" sx={{ mb: 2 }}>
                  Recent exports
                </Typography>
                {(exports.data?.content ?? []).map((request) => (
                  <ExportRow key={request.id} request={request} />
                ))}
              </CardContent>
            </Card>
          )}
        </Box>
      </Box>

      <Menu
        anchorEl={exportAnchor}
        open={exportAnchor !== null}
        onClose={() => setExportAnchor(null)}
      >
        {FORMATS.map((format) => (
          <MenuItem
            key={format}
            onClick={() => {
              setExportAnchor(null);
              runExport.mutate(format);
            }}
          >
            Export as {format}
          </MenuItem>
        ))}
      </Menu>
    </Box>
  );
}

function ExportRow({ request }: { request: ReportRequestSummary }) {
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        py: 1.5,
        borderTop: '1px solid',
        borderColor: 'divider',
      }}
    >
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="body2">{request.reportTitle}</Typography>
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
          {request.format} · {request.rowCount ?? '—'} rows ·{' '}
          {new Date(request.createdAt).toLocaleString('en-GB', {
            day: '2-digit',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit',
          })}
          {request.errorDetail ? ` · ${request.errorDetail}` : ''}
        </Typography>
      </Box>
      <StatusChip status={request.status} />
      <Button
        size="small"
        disabled={request.status !== 'READY'}
        onClick={() => void reportApi.download(request)}
      >
        Download
      </Button>
    </Box>
  );
}

function numeric(type: ColumnType): boolean {
  return type === 'NUMBER' || type === 'PERCENT' || type === 'SCORE' || type === 'DURATION';
}

/** The same formatting rules the exporters use, so the screen and the file agree. */
function format(value: unknown, type: ColumnType): string {
  if (value == null || value === '') {
    return '—';
  }
  switch (type) {
    case 'DURATION': {
      const minutes = Number(value);
      const hours = Math.floor(minutes / 60);
      const rest = minutes % 60;
      return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
    }
    case 'PERCENT':
      return `${Number(value).toFixed(2)}%`;
    case 'SCORE':
      return Number(value).toFixed(2);
    case 'NUMBER':
      return String(value);
    default:
      return String(value);
  }
}

/** A report should open on something useful: this month, and this year. */
function defaultsFor(definition: ReportDefinition): Filters {
  const today = new Date();
  const values: Filters = {};

  if (definition.filters.includes('FROM_DATE')) {
    values.fromDate = new Date(today.getFullYear(), today.getMonth(), 1)
      .toISOString()
      .slice(0, 10);
  }
  if (definition.filters.includes('TO_DATE')) {
    values.toDate = today.toISOString().slice(0, 10);
  }
  if (definition.filters.includes('YEAR')) {
    values.year = String(today.getFullYear());
  }
  if (definition.filters.includes('MONTH')) {
    values.month = String(today.getMonth() + 1);
  }
  if (definition.filters.includes('LIMIT')) {
    values.limit = '20';
  }
  return values;
}
